package com.fortytwohz.room;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomServiceTest {
    RoomRepository rooms = mock(RoomRepository.class);
    ParticipantRepository participants = mock(ParticipantRepository.class);
    AnswerRepository answers = mock(AnswerRepository.class);
    java.time.Clock clock = java.time.Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), java.time.ZoneOffset.UTC);
    RoomService service = new RoomService(rooms, participants, answers, clock);
    Room room = new Room("ABCDEFGH", clock.instant());
    Participant me;
    Participant partner = new Participant(room, "상대", "other");

    @BeforeEach void setup() throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("secret".getBytes(StandardCharsets.UTF_8)));
        me = new Participant(room, "나", hash);
        when(rooms.findByInviteCode("ABCDEFGH")).thenReturn(Optional.of(room));
        when(rooms.findLockedByCode("ABCDEFGH")).thenReturn(Optional.of(room));
        when(participants.findByRoomAndTokenHash(room, hash)).thenReturn(Optional.of(me));
    }

    @Test void expiredRoomRejectsPrivateReadsAndSharingBeforeReadingAnswers() {
        Instant created = clock.instant().minusSeconds(86401).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        ReflectionTestUtils.setField(room, "createdAt", created);
        ReflectionTestUtils.setField(room, "expiresAt", created.plusSeconds(86400));
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class,
                () -> service.answerState("ABCDEFGH", "Bearer secret", 0)).getStatusCode());
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class,
                () -> service.results("ABCDEFGH", "Bearer secret")).getStatusCode());
        var sharing = new SharedResultService(rooms, participants, answers, mock(SharedResultRepository.class), clock);
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class,
                () -> sharing.createShare("ABCDEFGH", "Bearer secret")).getStatusCode());
        verifyNoInteractions(answers, participants);
    }

    @Test void resultsUseOneAnswerQueryAndDoNotRevealUnpairedAnswers() {
        when(participants.findByRoomOrderByNicknameAsc(room)).thenReturn(List.of(me, partner));
        when(answers.findByRoomOrderByQuestionIndexAsc(room)).thenReturn(List.of(
                new Answer(room, partner, 0, "비공개", clock.instant()),
                new Answer(room, me, 1, "나의 답", clock.instant()), new Answer(room, partner, 1, "상대 답", clock.instant())));
        var result = service.results("ABCDEFGH", "Bearer secret");
        assertFalse(result.allComplete());
        assertNull(result.questions().get(0).partnerAnswer());
        assertEquals("상대 답", result.questions().get(1).partnerAnswer());
        verify(answers).findByRoomOrderByQuestionIndexAsc(room);
        verify(answers, never()).findByRoomAndQuestionIndex(any(), anyInt());
    }

    @Test void missingPreviousQuestionPreventsSubmission() {
        room.markReady();
        when(answers.findByRoomOrderByQuestionIndexAsc(room)).thenReturn(List.of());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.submitAnswer("ABCDEFGH", "Bearer secret", 1, "하루 종일 같이 보내기")).getStatusCode());
        verify(answers, never()).save(any());
    }

    @Test void duplicateSubmissionRemainsImmutable() {
        room.markReady();
        when(answers.findByRoomOrderByQuestionIndexAsc(room)).thenReturn(List.of());
        when(answers.findByParticipantAndQuestionIndex(me, 0))
                .thenReturn(Optional.of(new Answer(room, me, 0, "바로 다른 숙소를 찾는다", clock.instant())));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.submitAnswer("ABCDEFGH", "Bearer secret", 0, "상대방에게 먼저 상황을 알린다")).getStatusCode());
        verify(answers, never()).save(any());
    }

    @Test void completedResultAndShareUseSingleBulkReadEach() {
        room.markReady();
        var complete = IntStream.range(0, 13).boxed().flatMap(i ->
                List.of(new Answer(room, me, i, "같음", clock.instant()), new Answer(room, partner, i, "같음", clock.instant())).stream()).toList();
        when(participants.findByRoomOrderByNicknameAsc(room)).thenReturn(List.of(me, partner));
        when(answers.findByRoomOrderByQuestionIndexAsc(room)).thenReturn(complete);
        assertTrue(service.results("ABCDEFGH", "Bearer secret").allComplete());
        SharedResultRepository shared = mock(SharedResultRepository.class);
        when(shared.findByRoom(room)).thenReturn(Optional.empty());
        when(shared.save(any())).thenAnswer(call -> call.getArgument(0));
        var sharing = new SharedResultService(rooms, participants, answers, shared, clock);
        var response = sharing.createShare("ABCDEFGH", "Bearer secret");
        assertEquals(64, response.shareToken().length());
        verify(answers, times(2)).findByRoomOrderByQuestionIndexAsc(room);
        verify(answers, never()).findByRoomAndQuestionIndex(any(), anyInt());
        verify(shared).save(argThat(result -> result.getMatchedCount() == 12 && result.getDifferentCount() == 0));
    }
}

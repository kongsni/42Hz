package com.fortytwohz.room;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShareLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

    @Test void roomAndShareTimestampBoundariesAreExact() {
        Room room = new Room("BOUNDARY", NOW);
        assertFalse(room.isExpired(NOW));
        assertFalse(room.isExpired(NOW.plusSeconds(86400).minusNanos(1)));
        assertTrue(room.isExpired(NOW.plusSeconds(86400)));
        assertTrue(room.isExpired(NOW.minusNanos(1)));
        SharedResult share = new SharedResult(room, "token", 12, 0, NOW);
        assertFalse(share.isExpired(NOW));
        assertFalse(share.isExpired(NOW.plusSeconds(604800).minusNanos(1)));
        assertTrue(share.isExpired(NOW.plusSeconds(604800)));
        assertTrue(share.isExpired(NOW.plusSeconds(604800).plusNanos(1)));
        assertTrue(share.isExpired(NOW.minusNanos(1)));
    }

    @Test void nullInconsistentAndQuarantinedTimesFailClosed() {
        Room room = new Room("BOUNDARY", NOW);
        ReflectionTestUtils.setField(room, "expiresAt", NOW.plusSeconds(86401));
        assertTrue(room.isExpired(NOW));
        SharedResult share = new SharedResult(room, "token", 12, 0, NOW);
        ReflectionTestUtils.setField(share, "expiresAt", null);
        assertTrue(share.isExpired(NOW));
        ReflectionTestUtils.setField(share, "expiresAt", NOW.plusSeconds(604801));
        assertTrue(share.isExpired(NOW));
        ReflectionTestUtils.setField(share, "expiresAt", NOW.plusSeconds(604800));
        ReflectionTestUtils.setField(share, "retentionReviewRequired", true);
        assertTrue(share.isExpired(NOW));
    }

    @Test void microsecondStoragePrecisionDoesNotMakeNewRowsFutureDated() {
        Instant nanos = NOW.plusNanos(123456789);
        Room room = new Room("BOUNDARY", nanos);
        assertFalse(room.isExpired(nanos));
        assertFalse(new SharedResult(room, "token", 12, 0, nanos).isExpired(nanos));
    }

    @Test void expiredExistingShareCannotBeRenewedEvenInAnAccessibleRoom() {
        RoomRepository rooms = mock(RoomRepository.class);
        ParticipantRepository participants = mock(ParticipantRepository.class);
        AnswerRepository answers = mock(AnswerRepository.class);
        SharedResultRepository shares = mock(SharedResultRepository.class);
        Room room = new Room("BOUNDARY", NOW);
        room.markReady();
        when(rooms.findLockedByCode("BOUNDARY")).thenReturn(Optional.of(room));
        when(participants.findByRoomAndTokenHash(eq(room), anyString()))
                .thenReturn(Optional.of(new Participant(room, "나", "hash")));
        when(shares.findByRoom(room)).thenReturn(Optional.of(
                new SharedResult(room, "original", 12, 0, NOW.minusSeconds(604800))));
        var service = new SharedResultService(rooms, participants, answers, shares, Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class,
                () -> service.createShare("BOUNDARY", "Bearer secret")).getStatusCode());
        verify(shares, never()).save(any());
        verifyNoInteractions(answers);
    }
}

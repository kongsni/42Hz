package com.fortytwohz.room;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Service
public class RoomService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final RoomRepository rooms;
    private final ParticipantRepository participants;
    private final AnswerRepository answers;
    public RoomService(RoomRepository rooms, ParticipantRepository participants, AnswerRepository answers) {
        this.rooms = rooms; this.participants = participants; this.answers = answers;
    }
    @Transactional
    public CreateRoomResponse create(String nickname) {
        String code;
        do { code = randomString(ALPHABET, 8); } while (rooms.existsByInviteCode(code));
        Room room = rooms.save(new Room(code));
        String token = token();
        participants.save(new Participant(room, nickname.trim(), sha256(token)));
        return new CreateRoomResponse(code, token, "WAITING", "/room/" + code);
    }
    @Transactional
    public JoinRoomResponse join(String code, String nickname) {
        Room room = rooms.findLockedByCode(code).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
        if (room.isExpired()) throw error(HttpStatus.GONE, "만료된 방입니다.");
        if (room.getStatus() != RoomStatus.WAITING || participants.countByRoom(room) >= 2)
            throw error(HttpStatus.CONFLICT, "이미 두 명이 참여한 방입니다.");
        String token = token();
        participants.save(new Participant(room, nickname.trim(), sha256(token)));
        room.markReady();
        return new JoinRoomResponse(code, token, "READY");
    }
    @Transactional(readOnly = true)
    public RoomState state(String code, String bearer) {
        Room room = rooms.findByInviteCode(code).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
        if (room.isExpired()) throw error(HttpStatus.GONE, "만료된 방입니다.");
        if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length() <= 7)
            throw error(HttpStatus.UNAUTHORIZED, "참여자 인증이 필요합니다.");
        String hash = sha256(bearer.substring(7));
        Participant me = participants.findByRoomAndTokenHash(room, hash)
            .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "잘못된 참여자 토큰입니다."));
        List<ParticipantState> members = participants.findByRoomOrderByNicknameAsc(room).stream()
            .map(p -> new ParticipantState(p.getId(), p.getNickname(), p.getId().equals(me.getId()))).toList();
        return new RoomState(code, room.getStatus().name(), members);
    }

    @Transactional
    public AnswerState submitAnswer(String code, String bearer, int questionIndex, String answerText) {
        Room room = rooms.findByInviteCode(code).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
        Participant me = authenticate(room, bearer);
        if (room.getStatus() != RoomStatus.READY) throw error(HttpStatus.CONFLICT, "두 사람이 모두 입장해야 체험을 시작할 수 있습니다.");
        if (questionIndex < 0 || questionIndex > 12) throw error(HttpStatus.BAD_REQUEST, "잘못된 질문 번호입니다.");
        String value = answerText == null ? "" : answerText.trim();
        if (value.isBlank()) throw error(HttpStatus.BAD_REQUEST, "답변을 입력해 주세요.");
        Answer current = answers.findByParticipantAndQuestionIndex(me, questionIndex).orElse(null);
        if (current == null) answers.save(new Answer(room, me, questionIndex, value)); else current.update(value);
        return answerStateFor(room, me, questionIndex);
    }

    @Transactional(readOnly = true)
    public AnswerState answerState(String code, String bearer, int questionIndex) {
        Room room = rooms.findByInviteCode(code).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
        Participant me = authenticate(room, bearer);
        if (questionIndex < 0 || questionIndex > 12) throw error(HttpStatus.BAD_REQUEST, "잘못된 질문 번호입니다.");
        return answerStateFor(room, me, questionIndex);
    }

    @Transactional(readOnly = true)
    public ResultsState results(String code, String bearer) {
        Room room = rooms.findByInviteCode(code).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
        Participant me = authenticate(room, bearer);
        List<Participant> members = participants.findByRoomOrderByNicknameAsc(room);
        List<ResultQuestion> items = new ArrayList<>();
        boolean allComplete = members.size() == 2;
        for (int i = 0; i < 13; i++) {
            List<Answer> list = answers.findByRoomAndQuestionIndex(room, i);
            boolean complete = list.size() == 2;
            allComplete &= complete;
            String mine = list.stream().filter(a -> a.getParticipant().getId().equals(me.getId())).map(Answer::getAnswer).findFirst().orElse(null);
            String theirs = list.stream().filter(a -> !a.getParticipant().getId().equals(me.getId())).map(Answer::getAnswer).findFirst().orElse(null);
            items.add(new ResultQuestion(i, mine, complete ? theirs : null, complete));
        }
        return new ResultsState(code, allComplete, items);
    }

    private AnswerState answerStateFor(Room room, Participant me, int questionIndex) {
        List<Answer> list = answers.findByRoomAndQuestionIndex(room, questionIndex);
        String mine = list.stream().filter(a -> a.getParticipant().getId().equals(me.getId())).map(Answer::getAnswer).findFirst().orElse(null);
        String theirs = list.stream().filter(a -> !a.getParticipant().getId().equals(me.getId())).map(Answer::getAnswer).findFirst().orElse(null);
        boolean both = list.size() >= 2;
        return new AnswerState(questionIndex, mine != null, both, mine, both ? theirs : null, list.size());
    }

    private Participant authenticate(Room room, String bearer) {
        if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length() <= 7)
            throw error(HttpStatus.UNAUTHORIZED, "참여자 인증이 필요합니다.");
        String hash = sha256(bearer.substring(7));
        return participants.findByRoomAndTokenHash(room, hash).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "잘못된 참여자 토큰입니다."));
    }

    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    private String token() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    private String randomString(String chars, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<length;i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public record CreateRoomResponse(String roomCode, String participantToken, String status, String invitePath) {}
    public record JoinRoomResponse(String roomCode, String participantToken, String status) {}
    public record ParticipantState(UUID id, String nickname, boolean me) {}
    public record RoomState(String roomCode, String status, List<ParticipantState> participants) {}
    public record AnswerState(int questionIndex, boolean submitted, boolean bothSubmitted, String myAnswer, String partnerAnswer, int submittedCount) {}
    public record ResultQuestion(int questionIndex, String myAnswer, String partnerAnswer, boolean complete) {}
    public record ResultsState(String roomCode, boolean allComplete, List<ResultQuestion> questions) {}
}

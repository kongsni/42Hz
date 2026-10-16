
package com.fortytwohz.room;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SharedResultService {

    private final RoomRepository rooms;
    private final ParticipantRepository participants;
    private final AnswerRepository answers;
    private final SharedResultRepository sharedResults;

    private final SecureRandom random = new SecureRandom();

    public SharedResultService(
            RoomRepository rooms,
            ParticipantRepository participants,
            AnswerRepository answers,
            SharedResultRepository sharedResults
    ) {
        this.rooms = rooms;
        this.participants = participants;
        this.answers = answers;
        this.sharedResults = sharedResults;
    }

    @Transactional
    public ShareResponse createShare(String code, String bearer) {

        Room room = rooms.findLockedByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "방을 찾을 수 없습니다."
                ));

        authenticate(room, bearer);

        if (room.getStatus() != RoomStatus.READY) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "두 사람이 모두 입장해야 합니다."
            );
        }

        SharedResult existing = sharedResults
                .findByRoom(room)
                .orElse(null);

        if (existing != null) {
            return new ShareResponse(
                    existing.getShareToken(),
                    "/share/" + existing.getShareToken()
            );
        }

        int matched = 0;
        int different = 0;

        Map<Integer, List<Answer>> byQuestion = answers.findByRoomOrderByQuestionIndexAsc(room)
                .stream().collect(Collectors.groupingBy(Answer::getQuestionIndex));
        for (int i = 0; i < QuestionCatalog.TOTAL_QUESTIONS; i++) {
            List<Answer> list = byQuestion.getOrDefault(i, List.of());

            if (list.size() != 2) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "두 사람이 모든 질문을 완료해야 공유할 수 있습니다."
                );
            }

            // 마지막 주관식 질문은 일치율 계산에서 제외
            if (i < QuestionCatalog.CHOICE_QUESTIONS) {
                String first = list.get(0).getAnswer();
                String second = list.get(1).getAnswer();

                if (first.equals(second)) {
                    matched++;
                } else {
                    different++;
                }
            }
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        String token = HexFormat.of().formatHex(bytes);

        SharedResult shared = sharedResults.save(
                new SharedResult(room, token, matched, different)
        );

        return new ShareResponse(
                shared.getShareToken(),
                "/share/" + shared.getShareToken()
        );
    }

    @Transactional(readOnly = true)
    public PublicResult getSharedResult(String token) {

        SharedResult shared = sharedResults
                .findByShareToken(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "공유 결과를 찾을 수 없습니다."
                ));

        int matched = shared.getMatchedCount();
        int different = shared.getDifferentCount();

        int total = matched + different;
        int similarity = total == 0
                ? 0
                : (int) Math.round(matched * 100.0 / total);

        String title = similarity >= 75
                ? "말하지 않아도 닮은 두 사람"
                : similarity >= 40
                  ? "서로 다른 리듬, 같은 방향"
                  : "서로에게 새로운 세계가 되어주는 두 사람";

        String connection = similarity >= 75
                ? "일상에서 비슷한 선택을 하는 순간이 많았어요. 익숙한 편안함 속에서도 서로에게 새로운 질문을 건네 보세요."
                : similarity >= 40
                  ? "같은 선택도, 다른 선택도 있었어요. 닮은 부분은 편안함이 되고 다른 부분은 이야깃거리가 될 수 있겠네요."
                  : "선택이 달랐던 순간이 더 많았어요. 정답이 다른 게 아니라 서로를 더 알아갈 장면이 그만큼 많다는 뜻일지도 몰라요.";

        return new PublicResult(
                title,
                connection,
                matched,
                different,
                similarity
        );
    }

    private void authenticate(Room room, String bearer) {
        if (room.isExpired()) {
            throw new ResponseStatusException(HttpStatus.GONE, "만료된 방입니다.");
        }

        if (bearer == null
                || !bearer.startsWith("Bearer ")
                || bearer.length() <= 7) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "참여자 인증이 필요합니다."
            );
        }

        String token = bearer.substring(7);
        String hash;

        try {
            hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        participants.findByRoomAndTokenHash(room, hash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "잘못된 참여자 토큰입니다."
                ));
    }

    public record ShareResponse(
            String shareToken,
            String sharePath
    ) {}

    public record PublicResult(
            String title,
            String connection,
            int matched,
            int different,
            int similarity
    ) {}
}
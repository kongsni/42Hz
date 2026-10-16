package com.fortytwohz.room;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Current question set. Preserve option text until a versioned ID-based API is introduced. */
final class QuestionCatalog {
    static final int CHOICE_QUESTIONS = 12;
    static final int TOTAL_QUESTIONS = 13;
    private static final List<Set<String>> OPTIONS = List.of(
            Set.of("바로 다른 숙소를 찾는다", "상대방에게 먼저 상황을 알린다", "주변에서 해결할 방법을 찾는다", "잠깐 멍해진 뒤 생각한다"),
            Set.of("하루 종일 같이 보내기", "각자 쉬다가 중간중간 연락하기", "같은 공간에서 각자 할 일 하기", "그날 기분에 따라 정하기"),
            Set.of("바로 어떤 부분이 서운했는지 말한다", "조금 생각한 뒤 차분히 말한다", "상대방의 의도를 먼저 물어본다", "일단 넘기고 분위기를 본다"),
            Set.of("근처를 함께 구경한다", "카페에서 오래 이야기한다", "각자 하고 싶었던 일을 한다", "즉흥적으로 새로운 곳을 찾아본다"),
            Set.of("해결 방법을 같이 찾아주기", "말 끊지 않고 들어주기", "기분 전환할 일을 제안하기", "말없이 곁에 있어주기"),
            Set.of("평소 갖고 싶다고 했던 물건", "내 취향을 세심하게 기억한 것", "함께한 추억이 담긴 것", "예상하지 못한 깜짝 이벤트"),
            Set.of("번갈아 가며 고른다", "둘 다 좋아할 만한 걸 찾는다", "이번엔 상대방 취향을 따른다", "각자 하나씩 보고 감상을 나눈다"),
            Set.of("바쁜가 보다 하고 기다린다", "짧게라도 괜찮은지 물어본다", "나중에 편할 때 연락하라고 한다", "상황을 알 수 있으면 마 음이 편하다"),
            Set.of("계획  없이 걷는 바닷가", "맛집과 카페를 찾아다니기", "자연 속에서 조용히 쉬기", "새로운 체험을 여러 개 해보기"),
            Set.of("잔잔하고  따뜻한 노래", "신나고 리듬감 있는 노래", "몽환적이고 분위기 있는 노래", "추억이 떠오르는 익숙한 노래"),
            Set.of("많이 웃 고 즐거웠기 때문에", "진솔한 이야기를 나눴기 때문에", "평소와 다른 경험을 했기 때문에", "별일 없었지만 편안했기 때문에"),
            Set.of("솔직하게 이야기하기", "서로의 시간을 존중하기", "작은 표현을 자주 하기", "말하지 않아도 살피려 노력하기")
    );

    private QuestionCatalog() {}

    static String validateAnswer(int index, String answer) {
        if (index < 0 || index >= TOTAL_QUESTIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 질문 번호입니다.");
        }
        String value = answer == null ? "" : answer.trim();
        if (value.isBlank() || value.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "답변은 1~1000자로 입력해 주세요.");
        }
        if (index < CHOICE_QUESTIONS && !OPTIONS.get(index).contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 질문의 선택지 중 하나를 선택해 주세요.");
        }
        return value;
    }
}

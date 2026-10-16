package com.fortytwohz.room;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class QuestionCatalogTest {
    @Test void acceptsCurrentOptionAndTrimsText() {
        assertEquals("바로 다른 숙소를 찾는다", QuestionCatalog.validateAnswer(0, "바로 다른 숙소를 찾는다"));
        assertEquals("함께한 하루", QuestionCatalog.validateAnswer(12, "  함께한 하루  "));
    }
    @Test void rejectsArbitraryOrWrongQuestionOptions() {
        assertThrows(ResponseStatusException.class, () -> QuestionCatalog.validateAnswer(0, "임의 선택지"));
        assertThrows(ResponseStatusException.class, () -> QuestionCatalog.validateAnswer(1, "바로 다른 숙소를 찾는다"));
    }
    @Test void rejectsInvalidIndicesBlankAndOversizedText() {
        assertThrows(ResponseStatusException.class, () -> QuestionCatalog.validateAnswer(-1, "답변"));
        assertThrows(ResponseStatusException.class, () -> QuestionCatalog.validateAnswer(13, "답변"));
        assertThrows(ResponseStatusException.class, () -> QuestionCatalog.validateAnswer(12, "  "));
        assertThrows(ResponseStatusException.class, () -> QuestionCatalog.validateAnswer(12, "a".repeat(1001)));
    }
}

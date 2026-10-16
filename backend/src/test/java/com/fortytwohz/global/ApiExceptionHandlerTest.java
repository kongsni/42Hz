package com.fortytwohz.global;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fortytwohz.room.RoomController;
import com.fortytwohz.room.RoomService;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTest {
    RoomService service = mock(RoomService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new RoomController(service))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test void returnsStructuredValidationAndMalformedJsonErrors() throws Exception {
        mvc.perform(post("/api/v1/rooms").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").isString());
        mvc.perform(post("/api/v1/rooms").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }
    @Test void missingQuestionIndexIsRejectedInsteadOfDefaultingToZero() throws Exception {
        mvc.perform(post("/api/v1/rooms/CODE/answers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"바로 다른 숙소를 찾는다\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void preservesExpectedStatusAndReason() throws Exception {
        when(service.results("CODE", null)).thenThrow(new ResponseStatusException(HttpStatus.GONE, "만료된 방입니다."));
        mvc.perform(get("/api/v1/rooms/CODE/results"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.detail").value("만료된 방입니다."));
    }
    @Test void sanitizesUnexpectedErrorsAndPreservesMethodStatus() throws Exception {
        when(service.results("CODE", null)).thenThrow(new IllegalStateException("secret database URL"));
        mvc.perform(get("/api/v1/rooms/CODE/results"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("일시적인 서버 오류입니다. 잠시 후 다시 시도해 주세요."));
        mvc.perform(put("/api/v1/rooms/CODE/results"))
                .andExpect(status().isMethodNotAllowed());
    }
}

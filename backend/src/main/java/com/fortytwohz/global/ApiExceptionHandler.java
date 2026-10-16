package com.fortytwohz.global;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> expected(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders()).body(
                ProblemDetail.forStatusAndDetail(ex.getStatusCode(),
                        ex.getReason() == null ? "요청을 처리할 수 없습니다." : ex.getReason()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail invalidRequest(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "입력 형식을 확인해 주세요. 닉네임은 1~16자, 답변은 1~1000자이며 질문 번호는 0~12입니다.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "이미 처리되었거나 현재 상태와 충돌하는 요청입니다. 상태를 확인해 주세요.");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ProblemDetail busy(PessimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        // Preserve framework statuses (405/415/etc.) without exposing internal messages.
        if (ex instanceof ErrorResponse error) {
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders()).body(
                    ProblemDetail.forStatusAndDetail(error.getStatusCode(), "요청 경로 또는 형식을 확인해 주세요."));
        }
        // Do not log request bodies, bearer tokens, or SQL exception messages.
        log.error("Unhandled API exception type={}", ex.getClass().getName());
        return ResponseEntity.internalServerError().body(ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 서버 오류입니다. 잠시 후 다시 시도해 주세요."));
    }
}

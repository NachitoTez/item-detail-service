package com.ignacioramirez.itemDetailService.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        LOGGER.warn("{}: {}", ex.getCode(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getStatus().getReasonPhrase());
        addCommonAttributes(problem, ex.getCode(), request);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        LOGGER.warn("VALIDATION_ERROR: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");

        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> {
                    var m = new java.util.LinkedHashMap<String, Object>();
                    m.put("field", fe.getField());
                    m.put("message", fe.getDefaultMessage());
                    m.put("rejectedValue", fe.getRejectedValue());
                    return m;
                })
                .toList();


        problem.setProperty("errors", errors);
        addCommonAttributes(problem, "VALIDATION_ERROR", request);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleParamValidation(ConstraintViolationException ex, HttpServletRequest request) {
        LOGGER.warn("CONSTRAINT_VIOLATION: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Constraint violation");
        problem.setDetail("Request parameters are invalid.");

        var errors = ex.getConstraintViolations().stream()
                .map(cv -> Map.of(
                        "field", cv.getPropertyPath().toString(),
                        "message", cv.getMessage(),
                        "rejectedValue", Objects.toString(cv.getInvalidValue(), null)
                ))
                .toList();

        problem.setProperty("errors", errors);
        addCommonAttributes(problem, "CONSTRAINT_VIOLATION", request);
        return problem;
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    ProblemDetail handleBadRequest(Exception ex, HttpServletRequest request) {
        LOGGER.warn("BAD_REQUEST: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Bad request");
        problem.setDetail(ex.getMessage());
        addCommonAttributes(problem, "BAD_REQUEST", request);
        return problem;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        LOGGER.warn("METHOD_NOT_SUPPORTED: {}", ex.getMethod());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        problem.setTitle("Method Not Allowed");
        problem.setDetail("HTTP method not supported: " + ex.getMethod());
        addCommonAttributes(problem, "METHOD_NOT_ALLOWED", request);
        return problem;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatusCode() instanceof HttpStatus hs ? hs : HttpStatus.valueOf(ex.getStatusCode().value());
        LOGGER.warn("RESPONSE_STATUS: {} {}", status.value(), ex.getReason());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getReason());
        problem.setTitle(status.getReasonPhrase());
        addCommonAttributes(problem, "RESPONSE_STATUS", request);
        return problem;
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    ProblemDetail handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        LOGGER.warn("NO_HANDLER: {}", ex.getRequestURL());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Not Found");
        problem.setDetail("Endpoint not found: " + ex.getRequestURL());
        addCommonAttributes(problem, "NO_HANDLER", request);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        LOGGER.error("UNEXPECTED_ERROR", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Unexpected error");
        problem.setDetail("Please try again later.");
        addCommonAttributes(problem, "UNEXPECTED_ERROR", request);
        return problem;
    }

    private void addCommonAttributes(ProblemDetail problem, String code, HttpServletRequest request) {
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setInstance(URI.create(request.getRequestURI()));
    }
}

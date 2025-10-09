package com.ignacioramirez.itemDetailService.exceptions;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ===================== Resolvers con tipos genéricos =====================

    @FunctionalInterface
    private interface TypedMessageErrorResolver<T extends Throwable> {
        ProblemDetail resolve(T ex, HttpServletRequest request);
    }

    /** Registro de resolvers con tipos específicos */
    private final Map<Class<? extends Throwable>, TypedMessageErrorResolver<?>> messageErrorResolvers;

    public GlobalExceptionHandler() {
        this.messageErrorResolvers = new LinkedHashMap<>();
        messageErrorResolvers.put(InvalidFormatException.class,
                (TypedMessageErrorResolver<InvalidFormatException>) this::handleInvalidFormat);
        messageErrorResolvers.put(UnrecognizedPropertyException.class,
                (TypedMessageErrorResolver<UnrecognizedPropertyException>) this::handleUnrecognizedProperty);
        messageErrorResolvers.put(MismatchedInputException.class,
                (TypedMessageErrorResolver<MismatchedInputException>) this::handleMismatchedInput);
    }

    // ===================== Shape helper =====================

    private void shapeProblem(ProblemDetail problem, String code) {
        problem.setType(null);
        problem.setInstance(null);
        problem.setProperty("code", code);
    }

    // ===================== API Exception =====================

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        LOGGER.warn("{}: {}", ex.getCode(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getStatus().getReasonPhrase());
        shapeProblem(problem, ex.getCode());
        return problem;
    }

    // ===================== Bean Validation (body) =====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        LOGGER.warn("VALIDATION_ERROR: Request to {} failed validation", request.getRequestURI());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setDetail("One or more fields have invalid values");

        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("field", fe.getField());
                    m.put("message", fe.getDefaultMessage());
                    m.put("rejectedValue", fe.getRejectedValue());
                    return m;
                })
                .toList();

        problem.setProperty("errors", errors);
        shapeProblem(problem, "VALIDATION_ERROR");

        errors.forEach(error ->
                LOGGER.warn("  → Field '{}': {} (rejected value: {})",
                        error.get("field"), error.get("message"), error.get("rejectedValue"))
        );

        return problem;
    }

    // ===================== Bean Validation (params) =====================

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleParamValidation(ConstraintViolationException ex, HttpServletRequest request) {
        LOGGER.warn("CONSTRAINT_VIOLATION: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid Request Parameters");
        problem.setDetail("One or more request parameters are invalid");

        var errors = ex.getConstraintViolations().stream()
                .map(cv -> Map.of(
                        "parameter", cv.getPropertyPath().toString(),
                        "message", cv.getMessage(),
                        "rejectedValue", Objects.toString(cv.getInvalidValue(), "null")
                ))
                .toList();

        problem.setProperty("errors", errors);
        shapeProblem(problem, "CONSTRAINT_VIOLATION");
        return problem;
    }

    // ===================== Message Not Readable =====================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable cause = ex.getCause();
        LOGGER.warn("MESSAGE_NOT_READABLE in {}: {}",
                request.getRequestURI(),
                cause != null ? cause.getClass().getSimpleName() : "null");

        return Optional.ofNullable(cause)
                .flatMap(c -> resolveMessageError(c, request))
                .orElseGet(this::createMalformedJsonProblem);
    }

    @SuppressWarnings("unchecked")
    private <T extends Throwable> Optional<ProblemDetail> resolveMessageError(T cause, HttpServletRequest request) {
        return messageErrorResolvers.entrySet().stream()
                .filter(entry -> entry.getKey().isAssignableFrom(cause.getClass()))
                .findFirst()
                .map(entry -> ((TypedMessageErrorResolver<T>) entry.getValue()).resolve(cause, request));
    }

    private ProblemDetail createMalformedJsonProblem() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Malformed JSON");
        problem.setDetail("The request body is not valid JSON or is empty");
        shapeProblem(problem, "MALFORMED_JSON");
        return problem;
    }

    // ===================== Resolvers específicos =====================

    private ProblemDetail handleMismatchedInput(MismatchedInputException mie, HttpServletRequest request) {
        String fieldPath = mie.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .collect(Collectors.joining("."));

        LOGGER.warn("Missing or invalid field '{}' in request to {}", fieldPath, request.getRequestURI());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Missing Required Field");
        problem.setDetail(String.format("Missing or invalid field: '%s'", fieldPath));
        problem.setProperty("field", fieldPath);
        shapeProblem(problem, "MISSING_FIELD");
        return problem;
    }

    private ProblemDetail handleUnrecognizedProperty(UnrecognizedPropertyException upe, HttpServletRequest request) {
        String fieldName = upe.getPropertyName();
        LOGGER.warn("Unrecognized field '{}' in request to {}", fieldName, request.getRequestURI());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Unknown Field");
        problem.setDetail(String.format("Field '%s' is not recognized", fieldName));
        problem.setProperty("field", fieldName);

        if (upe.getKnownPropertyIds() != null && !upe.getKnownPropertyIds().isEmpty()) {
            problem.setProperty("knownFields", upe.getKnownPropertyIds());
        }

        shapeProblem(problem, "UNKNOWN_FIELD");
        return problem;
    }

    private ProblemDetail handleInvalidFormat(InvalidFormatException ife, HttpServletRequest request) {
        String fieldPath = ife.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .collect(Collectors.joining("."));
        String expectedType = ife.getTargetType().getSimpleName();

        LOGGER.warn("Invalid format for field '{}' in request to {}. Expected: {}, Got: {}",
                fieldPath, request.getRequestURI(), expectedType, ife.getValue());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid Field Format");
        problem.setDetail(String.format("Field '%s' has invalid format. Expected type: %s", fieldPath, expectedType));
        problem.setProperty("field", fieldPath);
        problem.setProperty("expectedType", expectedType);
        problem.setProperty("providedValue", ife.getValue());
        shapeProblem(problem, "INVALID_FORMAT");
        return problem;
    }

    // ===================== Otros HTTP errors =====================

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        LOGGER.warn("MISSING_PARAMETER: Required parameter '{}' is missing in request to {}",
                ex.getParameterName(), request.getRequestURI());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Missing Required Parameter");
        problem.setDetail(String.format("Required parameter '%s' is missing", ex.getParameterName()));
        problem.setProperty("parameter", ex.getParameterName());
        problem.setProperty("expectedType", ex.getParameterType());
        shapeProblem(problem, "MISSING_PARAMETER");
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";

        LOGGER.warn("TYPE_MISMATCH: Parameter '{}' has wrong type in request to {}. Expected: {}, Got: {}",
                ex.getName(), request.getRequestURI(), expectedType, ex.getValue());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid Parameter Type");
        problem.setDetail(String.format("Parameter '%s' must be of type %s", ex.getName(), expectedType));
        problem.setProperty("parameter", ex.getName());
        problem.setProperty("expectedType", expectedType);
        problem.setProperty("providedValue", ex.getValue());
        shapeProblem(problem, "TYPE_MISMATCH");
        return problem;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        LOGGER.warn("METHOD_NOT_ALLOWED: {} method not supported for {}", ex.getMethod(), request.getRequestURI());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        problem.setTitle("Method Not Allowed");
        problem.setDetail(String.format("HTTP method %s is not supported for this endpoint", ex.getMethod()));
        shapeProblem(problem, "METHOD_NOT_ALLOWED");
        return problem;
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ProblemDetail handleNotFound(Exception ex, HttpServletRequest request) {
        LOGGER.warn("NOT_FOUND: {} {} - Endpoint does not exist", request.getMethod(), request.getRequestURI());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Endpoint Not Found");
        problem.setDetail(String.format("The endpoint %s %s does not exist", request.getMethod(), request.getRequestURI()));
        shapeProblem(problem, "NOT_FOUND");
        return problem;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatusCode() instanceof HttpStatus hs
                ? hs
                : HttpStatus.valueOf(ex.getStatusCode().value());

        LOGGER.warn("RESPONSE_STATUS: {} {}", status.value(), ex.getReason());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getReason());
        problem.setTitle(status.getReasonPhrase());
        shapeProblem(problem, "RESPONSE_STATUS");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        LOGGER.error("UNEXPECTED_ERROR in {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Please try again later.");
        shapeProblem(problem, "UNEXPECTED_ERROR");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        LOGGER.warn("INVALID_ARGUMENT in {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid Argument");
        problem.setDetail(ex.getMessage());
        shapeProblem(problem, "INVALID_ARGUMENT");
        return problem;
    }
}
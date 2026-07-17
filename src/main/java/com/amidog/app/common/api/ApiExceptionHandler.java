package com.amidog.app.common.api;

import com.amidog.app.admin.InvalidAdminRequestException;
import com.amidog.app.auth.PasswordRecoveryService;
import com.amidog.app.auth.RegistrationService;
import com.amidog.app.catalog.ServiceCodeNormalizer;
import com.amidog.app.scheduling.InvalidSchedulingRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String INVALID_REQUEST_MESSAGE =
            "La solicitud contiene datos inv\u00e1lidos.";
    private static final String NOT_FOUND_MESSAGE =
            "No se encontr\u00f3 el recurso solicitado.";
    private static final String ACCESS_DENIED_MESSAGE =
            "Acceso denegado.";
    private static final String INTERNAL_ERROR_MESSAGE =
            "Ocurri\u00f3 un error inesperado. Intenta nuevamente m\u00e1s tarde.";

    @ExceptionHandler(RegistrationService.InvalidVerificationTokenException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidVerificationToken() {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_VERIFICATION_TOKEN",
                RegistrationService.INVALID_TOKEN_MESSAGE);
    }

    @ExceptionHandler(PasswordRecoveryService.InvalidPasswordResetTokenException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidPasswordResetToken() {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_PASSWORD_RESET_TOKEN",
                PasswordRecoveryService.INVALID_TOKEN_MESSAGE);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<?> handleConflict(ConflictException exception) {
        if (exception.getDetails() != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    new DetailedConflictResponse(
                            exception.getCode(),
                            exception.getClientSafeMessage(),
                            Map.of(),
                            exception.getDetails()));
        }
        return error(
                HttpStatus.CONFLICT,
                exception.getCode(),
                exception.getClientSafeMessage());
    }

    @ExceptionHandler(InvalidSchedulingRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidSchedulingRequest(
            InvalidSchedulingRequestException exception) {
        return error(
                HttpStatus.BAD_REQUEST,
                exception.getType().code(),
                exception.getType().message());
    }

    @ExceptionHandler(InvalidAdminRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidAdminRequest(
            InvalidAdminRequestException exception) {
        return error(
                HttpStatus.BAD_REQUEST,
                exception.getType().code(),
                exception.getType().message());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ApiErrorResponse> handleRateLimit() {
        return error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", TooManyRequestsException.MESSAGE);
    }

    @ExceptionHandler(ServiceCodeNormalizer.InvalidServiceCodeException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidServiceCode() {
        return error(
                HttpStatus.BAD_REQUEST,
                "SERVICE_CODE_INVALID",
                "El código del servicio debe contener letras o números y tener hasta 60 caracteres.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidationError(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));

        return ResponseEntity.badRequest().body(
                new ApiErrorResponse("VALIDATION_ERROR", INVALID_REQUEST_MESSAGE, errors));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> handleInvalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", INVALID_REQUEST_MESSAGE);
    }

    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    ResponseEntity<ApiErrorResponse> handleUnknownApiRoute() {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", NOT_FOUND_MESSAGE);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied() {
        return error(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                ACCESS_DENIED_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        LOG.error(
                "Unexpected API failure correlationId={} exceptionType={} method={}",
                correlationId,
                exception.getClass().getName(),
                request.getMethod());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("X-Correlation-ID", correlationId)
                .body(ApiErrorResponse.of(
                        "INTERNAL_ERROR",
                        INTERNAL_ERROR_MESSAGE));
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message));
    }

    private record DetailedConflictResponse(
            String code,
            String message,
            Map<String, String> errors,
            ConflictException.ReservationConflictDetails details) {
    }
}

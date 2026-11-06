package com.cherrycore.shared.infrastructure.web;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.error.ErrorCode;

/**
 * Gestión centralizada de errores: toda excepción acaba en la respuesta KO
 * con su estado HTTP.
 *
 * <p>Reglas de seguridad:</p>
 * <ul>
 *   <li>Nunca se devuelve el mensaje interno de una excepción ni su traza.</li>
 *   <li>Nunca se devuelve el valor recibido en un campo inválido.</li>
 *   <li>Los errores inesperados se registran con un id de incidencia para
 *       poder encontrarlos en los logs.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String LOG_DOMAIN_ERROR = "Domain error [{}]";
    private static final String LOG_UNEXPECTED_ERROR = "Unexpected error [incidentId={}]";

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomain(DomainException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        LOGGER.debug(LOG_DOMAIN_ERROR, errorCode.code());
        return respond(errorCode, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException exception) {
        return respond(CommonErrorCode.VALIDATION_ERROR, toFieldErrors(exception.getBindingResult()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(HandlerMethodValidationException exception) {
        List<ApiFieldError> fields = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiFieldError(
                                result.getMethodParameter().getParameterName(),
                                ConstraintCodeConverter.toCode(lastCode(error.getCodes())))))
                .toList();
        return respond(CommonErrorCode.VALIDATION_ERROR, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable() {
        return respond(WebErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter() {
        return respond(WebErrorCode.MISSING_PARAMETER);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch() {
        return respond(WebErrorCode.INVALID_PARAMETER);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound() {
        return respond(WebErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed() {
        return respond(WebErrorCode.METHOD_NOT_ALLOWED);
    }

    /** Fichero por encima de spring.servlet.multipart.max-file-size (p. ej. un logo de más de 2 MB). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleFileTooLarge() {
        return respond(WebErrorCode.FILE_TOO_LARGE);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType() {
        return respond(WebErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    /*
     * Lanzadas por la seguridad a nivel de método (@PreAuthorize). Sin estos
     * manejadores caerían en el genérico y devolverían 500.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication() {
        return respond(CommonErrorCode.AUTHENTICATION_REQUIRED, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied() {
        return respond(CommonErrorCode.ACCESS_DENIED, List.of());
    }

    /*
     * Dos peticiones han modificado el mismo registro a la vez (columna version).
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrentModification() {
        return respond(CommonErrorCode.CONCURRENT_MODIFICATION, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        LOGGER.error(LOG_UNEXPECTED_ERROR, UUID.randomUUID(), exception);
        return respond(CommonErrorCode.INTERNAL_ERROR, List.of());
    }

    private static ResponseEntity<ApiErrorResponse> respond(ErrorCode errorCode, List<ApiFieldError> fields) {
        HttpStatus status = HttpStatusResolver.resolve(errorCode);
        return ResponseEntity.status(status).body(ApiErrorResponse.of(errorCode, fields));
    }

    private static ResponseEntity<ApiErrorResponse> respond(WebErrorCode errorCode) {
        return ResponseEntity.status(errorCode.status()).body(ApiErrorResponse.of(errorCode));
    }

    private static List<ApiFieldError> toFieldErrors(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .map(error -> new ApiFieldError(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        ConstraintCodeConverter.toCode(error.getCode())))
                .toList();
    }

    /*
     * Spring devuelve los códigos de más específico a más genérico
     * (p. ej. "NotBlank.request.email", ..., "NotBlank"). El último es el
     * nombre de la validación.
     */
    private static String lastCode(String[] codes) {
        return codes == null || codes.length == 0 ? null : codes[codes.length - 1];
    }
}

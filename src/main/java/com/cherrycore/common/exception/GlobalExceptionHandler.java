package com.cherrycore.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.cherrycore.car.exception.CarNotFoundException;
import com.cherrycore.common.api.ApiError;
import com.cherrycore.common.api.ApiErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_ERROR_MESSAGE =
            "Request validation failed";

    private static final String MALFORMED_REQUEST_MESSAGE =
            "Request body is invalid or malformed";

    private static final String INTERNAL_SERVER_ERROR_MESSAGE =
            "An unexpected internal error occurred";

    private static final String UNEXPECTED_ERROR_LOG_MESSAGE =
            "Unexpected error while processing the request";

    /*
     * Gestiona el caso en el que se solicita un coche inexistente.
     */
    @ExceptionHandler(CarNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCarNotFound(
            CarNotFoundException exception
    ) {
        ApiError error = new ApiError(
                ErrorCode.CAR_NOT_FOUND.name(),
                exception.getMessage(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.error(error));
    }

    /*
     * Gestiona los errores producidos por las anotaciones de validación
     * de los DTOs: @NotBlank, @NotNull, @Size, etc.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> details = new LinkedHashMap<>();

        for (FieldError fieldError
                : exception.getBindingResult().getFieldErrors()) {

            details.put(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            );
        }

        ApiError error = new ApiError(
                ErrorCode.VALIDATION_ERROR.name(),
                VALIDATION_ERROR_MESSAGE,
                details
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.error(error));
    }

    /*
     * Gestiona un JSON incorrecto, incompleto sintácticamente
     * o con tipos incompatibles.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            HttpMessageNotReadableException exception
    ) {
        ApiError error = new ApiError(
                ErrorCode.MALFORMED_REQUEST.name(),
                MALFORMED_REQUEST_MESSAGE,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.error(error));
    }

    /*
     * Captura cualquier error inesperado que no haya sido gestionado
     * por un método más específico.
     *
     * No devolvemos exception.getMessage() porque podría exponer
     * información interna de la aplicación.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedError(
            Exception exception
    ) {
        LOGGER.error(UNEXPECTED_ERROR_LOG_MESSAGE, exception);

        ApiError error = new ApiError(
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                INTERNAL_SERVER_ERROR_MESSAGE,
                null
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.error(error));
    }
}
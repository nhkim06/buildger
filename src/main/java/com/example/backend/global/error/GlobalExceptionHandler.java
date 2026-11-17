package com.example.backend.global.error;

import com.example.backend.global.error.ErrorResponse.FieldErrorDetail;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest request) {
        return respond(e.getErrorCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException e, HttpServletRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        e.getFieldErrors().forEach(fe -> errors.add(new FieldErrorDetail(fe.getField(), fe.getDefaultMessage())));
        e.getGlobalErrors().forEach(ge -> errors.add(new FieldErrorDetail(ge.getObjectName(), ge.getDefaultMessage())));
        return respond(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage(), request, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e,
                                                                HttpServletRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        e.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(err -> errors.add(new FieldErrorDetail(name, err.getDefaultMessage())));
        });
        return respond(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage(), request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e,
                                                                   HttpServletRequest request) {
        List<FieldErrorDetail> errors = e.getConstraintViolations().stream()
                .map(v -> new FieldErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return respond(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage(), request, errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest request) {
        return respond(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage(), request, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return respond(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getMessage(), request, null);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException e, HttpServletRequest request) {
        return respond(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage(), request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e,
                                                         HttpServletRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage(), request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e,
                                                      HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage(), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage(), request, null);
    }

    /** 잠금 대기 시간 초과·교착 상태 (docs/api.md 4.5) */
    @ExceptionHandler({PessimisticLockingFailureException.class, PessimisticLockException.class,
            LockTimeoutException.class})
    public ResponseEntity<ErrorResponse> handleLock(Exception e, HttpServletRequest request) {
        log.warn("Lock acquisition failed: {} {}", request.getMethod(), request.getRequestURI());
        return respond(ErrorCode.CONCURRENT_MODIFICATION, ErrorCode.CONCURRENT_MODIFICATION.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected error: {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), request, null);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message, HttpServletRequest request,
                                                  List<FieldErrorDetail> errors) {
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, message, request.getRequestURI(), errors));
    }
}

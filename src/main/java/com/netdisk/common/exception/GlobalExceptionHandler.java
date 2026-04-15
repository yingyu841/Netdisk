package com.netdisk.common.exception;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException ex, HttpServletRequest request) {
        request.setAttribute(RequestIdFilter.BIZ_CODE_KEY, ex.getCode());
        request.setAttribute(RequestIdFilter.BIZ_MESSAGE_KEY, ex.getMessage());
        log.warn("biz exception requestId={} code={} message={}", requestId(request), ex.getCode(), ex.getMessage());
        return new ApiResponse<>(ex.getCode(), ex.getMessage(), null, requestId(request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        if (msg == null || msg.trim().isEmpty()) {
            msg = "请求体不合法";
        }
        request.setAttribute(RequestIdFilter.BIZ_CODE_KEY, ErrorCode.INVALID_PARAM);
        request.setAttribute(RequestIdFilter.BIZ_MESSAGE_KEY, msg);
        log.warn("validation exception requestId={} message={}", requestId(request), msg);
        return new ApiResponse<>(ErrorCode.INVALID_PARAM, msg, null, requestId(request));
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnknown(Exception ex, HttpServletRequest request) {
        request.setAttribute(RequestIdFilter.BIZ_CODE_KEY, ErrorCode.INTERNAL);
        request.setAttribute(RequestIdFilter.BIZ_MESSAGE_KEY, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        log.error("unexpected exception requestId={}", requestId(request), ex);
        return new ApiResponse<>(ErrorCode.INTERNAL, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), null, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}

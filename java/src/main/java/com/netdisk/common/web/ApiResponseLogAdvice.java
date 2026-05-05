package com.netdisk.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.servlet.http.HttpServletRequest;

/**
 * 记录统一响应的业务码与业务信息，供访问日志输出。
 */
@Component
@ControllerAdvice
public class ApiResponseLogAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof ApiResponse && request instanceof ServletServerHttpRequest) {
            ApiResponse<?> apiResponse = (ApiResponse<?>) body;
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            servletRequest.setAttribute(RequestIdFilter.BIZ_CODE_KEY, apiResponse.getCode());
            servletRequest.setAttribute(RequestIdFilter.BIZ_MESSAGE_KEY, apiResponse.getMessage());
        }
        return body;
    }
}

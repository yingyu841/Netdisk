package com.netdisk.common.web;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class RequestIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("biz.access");
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String BIZ_CODE_KEY = "bizCode";
    public static final String BIZ_MESSAGE_KEY = "bizMessage";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startAt = System.currentTimeMillis();
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute(REQUEST_ID_KEY, requestId);
        response.setHeader("X-Request-Id", requestId);
        MDC.put(REQUEST_ID_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMs = System.currentTimeMillis() - startAt;
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            String fullPath = (query == null || query.trim().isEmpty()) ? uri : uri + "?" + query;
            String clientIp = request.getRemoteAddr();
            String userId = String.valueOf(request.getAttribute("authUserId"));
            Object bizCode = request.getAttribute(BIZ_CODE_KEY);
            Object bizMessage = request.getAttribute(BIZ_MESSAGE_KEY);
            log.info("access method={} path={} status={} costMs={} ip={} userId={} requestId={} bizCode={} bizMessage={}",
                    method,
                    fullPath,
                    response.getStatus(),
                    costMs,
                    clientIp,
                    userId == null ? "" : userId,
                    requestId,
                    bizCode == null ? "" : bizCode,
                    bizMessage == null ? "" : bizMessage);
            MDC.remove(REQUEST_ID_KEY);
        }
    }
}

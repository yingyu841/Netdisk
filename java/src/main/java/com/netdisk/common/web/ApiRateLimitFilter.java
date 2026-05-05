package com.netdisk.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netdisk.common.ErrorCode;
import com.netdisk.config.AppProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 简单 Redis 固定窗口限流：优先保护高风险接口。
 */
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public ApiRateLimitFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, AppProperties appProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Rule rule = resolveRule(request);
        if (rule == null || redisTemplate == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = clientIp(request);
        String redisKey = "ratelimit:" + rule.key + ":" + clientIp;
        Long current;
        try {
            current = redisTemplate.opsForValue().increment(redisKey, 1L);
            if (current != null && current.longValue() == 1L) {
                redisTemplate.expire(redisKey, rule.windowSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            // 限流系统异常时默认放行，避免影响核心链路。
            filterChain.doFilter(request, response);
            return;
        }

        if (current != null && current.longValue() > rule.maxRequests) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ApiResponse<Void> body = new ApiResponse<Void>(
                    ErrorCode.TOO_MANY_REQUESTS,
                    "请求过于频繁，请稍后重试",
                    null,
                    requestId(request));
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Rule resolveRule(HttpServletRequest request) {
        if (appProperties == null || appProperties.getRateLimit() == null || !appProperties.getRateLimit().isEnabled()) {
            return null;
        }
        String method = request.getMethod();
        String uri = request.getRequestURI();
        for (AppProperties.RateLimit.Rule cfg : appProperties.getRateLimit().getRules()) {
            if (cfg == null) {
                continue;
            }
            String cfgMethod = safe(cfg.getMethod());
            String cfgPattern = safe(cfg.getPathPattern());
            if (cfgMethod.isEmpty() || cfgPattern.isEmpty()) {
                continue;
            }
            if (!cfgMethod.equalsIgnoreCase(method)) {
                continue;
            }
            if (!PATH_MATCHER.match(cfgPattern, uri)) {
                continue;
            }
            int maxRequests = cfg.getMaxRequests() <= 0 ? 1 : cfg.getMaxRequests();
            int windowSeconds = cfg.getWindowSeconds() <= 0 ? 1 : cfg.getWindowSeconds();
            String key = safe(cfg.getKey()).isEmpty() ? cfgMethod + ":" + cfgPattern : cfg.getKey();
            return new Rule(key, maxRequests, windowSeconds);
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            String[] parts = xff.split(",");
            if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                return parts[0].trim();
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private static class Rule {
        private final String key;
        private final int maxRequests;
        private final int windowSeconds;

        private Rule(String key, int maxRequests, int windowSeconds) {
            this.key = key;
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
        }
    }
}

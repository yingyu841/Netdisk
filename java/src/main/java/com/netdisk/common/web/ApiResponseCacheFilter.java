package com.netdisk.common.web;

import com.netdisk.config.AppProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 全局 GET JSON 响应短缓存，并在写操作后进行同作用域失效。
 */
public class ApiResponseCacheFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    public ApiResponseCacheFilter(StringRedisTemplate redisTemplate, AppProperties appProperties) {
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (redisTemplate == null) {
            filterChain.doFilter(request, response);
            return;
        }
        AppProperties.ResponseCache cfg = appProperties == null ? null : appProperties.getResponseCache();
        if (cfg == null || !cfg.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String method = request.getMethod();
        String uri = request.getRequestURI();
        if (!isApiRoute(uri) || isCacheExcluded(uri, cfg)) {
            filterChain.doFilter(request, response);
            return;
        }
        String scope = cacheScope(request);
        if (isWriteMethod(method)) {
            evictScope(scope);
            filterChain.doFilter(request, response);
            return;
        }
        if (!"GET".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!isCacheAllowed(uri, cfg)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = cacheKey(scope, request);
        String cached = readCache(key);
        if (cached != null) {
            response.setStatus(200);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getWriter().write(cached);
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrapper);
        String contentType = wrapper.getContentType();
        if (wrapper.getStatus() == 200 && isJson(contentType)) {
            byte[] bytes = wrapper.getContentAsByteArray();
            if (bytes != null && bytes.length > 0 && bytes.length <= cfg.getMaxBodyBytes()) {
                writeCache(scope, key, new String(bytes, StandardCharsets.UTF_8), cfg.getTtlSeconds(), cfg.getIndexTtlSeconds());
            }
        }
        wrapper.copyBodyToResponse();
    }

    private boolean isApiRoute(String uri) {
        if (uri == null) {
            return false;
        }
        return uri.startsWith("/api/") || uri.startsWith("/s/");
    }

    private boolean isCacheExcluded(String uri, AppProperties.ResponseCache cfg) {
        if (uri == null) {
            return true;
        }
        List<String> exacts = cfg.getExcludeExactPaths();
        if (exacts != null) {
            for (String exact : exacts) {
                if (exact != null && exact.equals(uri)) {
                    return true;
                }
            }
        }
        List<String> prefixes = cfg.getExcludePathPrefixes();
        if (prefixes != null) {
            for (String prefix : prefixes) {
                if (prefix != null && !prefix.trim().isEmpty() && uri.startsWith(prefix)) {
                    return true;
                }
            }
        }
        List<String> suffixes = cfg.getExcludePathSuffixes();
        if (suffixes != null) {
            for (String suffix : suffixes) {
                if (suffix != null && !suffix.trim().isEmpty() && uri.endsWith(suffix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCacheAllowed(String uri, AppProperties.ResponseCache cfg) {
        if (uri == null || uri.trim().isEmpty()) {
            return false;
        }
        List<String> prefixes = cfg.getIncludePathPrefixes();
        if (prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        for (String prefix : prefixes) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private String cacheScope(HttpServletRequest request) {
        Object uid = request.getAttribute("authUserId");
        if (uid != null) {
            return "user:" + String.valueOf(uid);
        }
        return "public";
    }

    private String cacheKey(String scope, HttpServletRequest request) {
        String query = request.getQueryString();
        String full = request.getRequestURI() + (query == null ? "" : "?" + query);
        return "api:resp:scope:" + scope + ":url:" + full;
    }

    private String readCache(String key) {
        try {
            String v = redisTemplate.opsForValue().get(key);
            if (v == null || v.trim().isEmpty()) {
                return null;
            }
            return v;
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeCache(String scope, String key, String value, long ttlSeconds, long indexTtlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
            String indexKey = scopeIndexKey(scope);
            redisTemplate.opsForSet().add(indexKey, key);
            redisTemplate.expire(indexKey, indexTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception ignore) {
        }
    }

    private void evictScope(String scope) {
        try {
            String indexKey = scopeIndexKey(scope);
            Set<String> keys = redisTemplate.opsForSet().members(indexKey);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            redisTemplate.delete(indexKey);
        } catch (Exception ignore) {
        }
    }

    private String scopeIndexKey(String scope) {
        return "api:resp:scope-index:" + scope;
    }

    private boolean isJson(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return false;
        }
        String normalized = contentType.toLowerCase();
        return normalized.contains("application/json") || normalized.contains("+json");
    }
}

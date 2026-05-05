package com.netdisk.common.web;

import com.netdisk.config.AppProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 记录接口耗时，输出慢请求日志，并上报 Micrometer 指标。
 */
public class ApiSlowRequestFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("biz.performance.slowapi");

    private final MeterRegistry meterRegistry;
    private final AppProperties appProperties;

    public ApiSlowRequestFilter(MeterRegistry meterRegistry, AppProperties appProperties) {
        this.meterRegistry = meterRegistry;
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNs = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedNs = System.nanoTime() - startNs;
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNs);

            List<Tag> tags = buildTags(request, response);
            Timer.builder("netdisk.api.request.duration")
                    .description("API request duration")
                    .tags(tags)
                    .register(meterRegistry)
                    .record(elapsedNs, TimeUnit.NANOSECONDS);

            long threshold = appProperties.getSlowApiThresholdMs();
            if (appProperties.isSlowApiEnabled() && elapsedMs >= threshold) {
                meterRegistry.counter("netdisk.api.request.slow.total", tags).increment();
                log.warn(
                        "slow api request method={} uri={} status={} costMs={} thresholdMs={} requestId={} userId={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        elapsedMs,
                        threshold,
                        requestId(request),
                        userId(request)
                );
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/actuator");
    }

    private List<Tag> buildTags(HttpServletRequest request, HttpServletResponse response) {
        List<Tag> tags = new ArrayList<Tag>();
        tags.add(Tag.of("method", safe(request.getMethod())));
        tags.add(Tag.of("uri", normalizeUri(request.getRequestURI())));
        tags.add(Tag.of("status", String.valueOf(response.getStatus())));
        return tags;
    }

    private String normalizeUri(String uri) {
        String val = safe(uri);
        val = val.replaceAll("[0-9]{2,}", "{id}");
        val = val.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F\\-]{27,}", "{uuid}");
        if (val.length() > 120) {
            return val.substring(0, 120);
        }
        return val;
    }

    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }

    private String userId(HttpServletRequest request) {
        Object val = request.getAttribute("authUserId");
        return val == null ? "" : String.valueOf(val);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}

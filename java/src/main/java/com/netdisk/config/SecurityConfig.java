package com.netdisk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netdisk.common.web.ApiRateLimitFilter;
import com.netdisk.common.web.ApiResponseCacheFilter;
import com.netdisk.common.web.ApiSlowRequestFilter;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.security.JwtAuthFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;
import java.util.Collections;

@Configuration
public class SecurityConfig {
    @Bean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            RequestIdFilter requestIdFilter,
            ApiRateLimitFilter apiRateLimitFilter,
            ApiResponseCacheFilter apiResponseCacheFilter,
            ApiSlowRequestFilter apiSlowRequestFilter) throws Exception {
        http.cors()
                .and()
                .csrf().disable()
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeRequests(c -> c
                        .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .antMatchers(
                                "/healthz",
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/actuator/metrics",
                                "/s/**",
                                "/api/v1/file-access",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/verification/send",
                                "/api/v2/public/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthFilter, RequestIdFilter.class)
                .addFilterAfter(apiRateLimitFilter, JwtAuthFilter.class)
                .addFilterAfter(apiResponseCacheFilter, ApiRateLimitFilter.class)
                .addFilterAfter(apiSlowRequestFilter, ApiResponseCacheFilter.class);
        return http.build();
    }

    @Bean
    public ApiRateLimitFilter apiRateLimitFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AppProperties appProperties) {
        return new ApiRateLimitFilter(redisTemplate, objectMapper, appProperties);
    }

    @Bean
    public ApiResponseCacheFilter apiResponseCacheFilter(StringRedisTemplate redisTemplate, AppProperties appProperties) {
        return new ApiResponseCacheFilter(redisTemplate, appProperties);
    }

    @Bean
    public ApiSlowRequestFilter apiSlowRequestFilter(MeterRegistry meterRegistry, AppProperties appProperties) {
        return new ApiSlowRequestFilter(meterRegistry, appProperties);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 本地联调阶段允许前端 dev server 跨域访问
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

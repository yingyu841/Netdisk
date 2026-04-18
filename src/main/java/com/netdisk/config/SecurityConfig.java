package com.netdisk.config;

import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter, RequestIdFilter requestIdFilter) throws Exception {
        http.csrf().disable()
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeRequests(c -> c
                        .antMatchers("/healthz", "/s/**", "/api/v1/file-access", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/verification/send").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthFilter, RequestIdFilter.class);
        return http.build();
    }
}

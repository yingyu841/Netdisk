package com.netdisk.controller;

import com.netdisk.pojo.dto.LoginRequest;
import com.netdisk.pojo.dto.RefreshRequest;
import com.netdisk.pojo.dto.RegisterRequest;
import com.netdisk.pojo.dto.SendVerificationRequest;
import com.netdisk.pojo.vo.LoginResponseVO;
import com.netdisk.pojo.vo.SessionVO;
import com.netdisk.pojo.vo.TokenVO;
import com.netdisk.pojo.vo.UserProfileVO;
import com.netdisk.service.AuthService;
import com.netdisk.service.VerificationService;
import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证相关接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final VerificationService verificationService;

    public AuthController(AuthService authService, VerificationService verificationService) {
        this.authService = authService;
        this.verificationService = verificationService;
    }

    /**
     * 注册接口。
     */
    @PostMapping("/register")
    public ApiResponse<UserProfileVO> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest req) {
        return ApiResponse.ok(authService.register(request), requestId(req));
    }

    /**
     * 发送验证码接口。
     */
    @PostMapping("/verification/send")
    public ApiResponse<Void> sendVerification(@Valid @RequestBody SendVerificationRequest request, HttpServletRequest req) {
        verificationService.send(request.getChannel(), request.getEmail(), request.getMobile());
        return ApiResponse.ok(null, requestId(req));
    }

    /**
     * 登录接口。
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponseVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest req) {
        return ApiResponse.ok(authService.login(request), requestId(req));
    }

    /**
     * 刷新令牌接口。
     */
    @PostMapping("/refresh")
    public ApiResponse<TokenVO> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest req) {
        return ApiResponse.ok(authService.refresh(request.getRefreshToken()), requestId(req));
    }

    /**
     * 退出登录接口。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(String.valueOf(request.getAttribute("authSessionId")));
        return ApiResponse.ok(null, requestId(request));
    }

    /**
     * 查询会话列表接口。
     */
    @GetMapping("/sessions")
    public ApiResponse<List<SessionVO>> sessions(HttpServletRequest request) {
        String userId = String.valueOf(request.getAttribute("authUserId"));
        return ApiResponse.ok(authService.listSessions(userId), requestId(request));
    }

    /**
     * 删除会话接口。
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId, HttpServletRequest request) {
        authService.deleteSession(String.valueOf(request.getAttribute("authUserId")), sessionId);
        return ApiResponse.ok(null, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}

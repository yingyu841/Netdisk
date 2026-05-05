package com.netdisk.service.impl;

import com.netdisk.pojo.dto.LoginRequestDTO;
import com.netdisk.pojo.dto.RegisterRequestDTO;
import com.netdisk.pojo.entity.User;
import com.netdisk.pojo.entity.UserSession;
import com.netdisk.mapper.UserMapper;
import com.netdisk.mapper.UserSessionMapper;
import com.netdisk.pojo.vo.LoginResponseVO;
import com.netdisk.pojo.vo.SessionVO;
import com.netdisk.pojo.vo.TokenVO;
import com.netdisk.pojo.vo.UserProfileVO;
import com.netdisk.security.JwtTokenProvider;
import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.config.AppProperties;
import com.netdisk.service.AuthService;
import com.netdisk.service.UserResourceInitService;
import com.netdisk.service.VerificationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 认证与会话服务实现类。
 */
@Service
public class AuthServiceImpl implements AuthService {
    private final UserMapper userRepository;
    private final UserSessionMapper sessionRepository;
    private final VerificationService verificationService;
    private final UserResourceInitService userResourceInitService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppProperties properties;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthServiceImpl(UserMapper userRepository, UserSessionMapper sessionRepository, VerificationService verificationService,
                           UserResourceInitService userResourceInitService, JwtTokenProvider jwtTokenProvider, AppProperties properties) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.verificationService = verificationService;
        this.userResourceInitService = userResourceInitService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.properties = properties;
    }

    @Override
    @Transactional
    public UserProfileVO register(RegisterRequestDTO req) {
        String email = safeLower(req.getEmail());
        String mobile = trim(req.getMobile());
        boolean hasEmail = !email.isEmpty();
        boolean hasMobile = !mobile.isEmpty();
        if (hasEmail == hasMobile || req.getPassword() == null || req.getPassword().length() < 8) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "邮箱或手机号填写不正确");
        }
        if (hasEmail && userRepository.countByEmail(email) > 0) {
            throw new BizException(ErrorCode.CONFLICT, 409, "邮箱或手机号已被注册");
        }
        if (hasMobile && userRepository.countByMobile(mobile) > 0) {
            throw new BizException(ErrorCode.CONFLICT, 409, "邮箱或手机号已被注册");
        }
        String channel = hasEmail ? "email" : "sms";
        if (!verificationService.verifyAndConsume(channel, email, mobile, req.getVerificationCode())) {
            throw new BizException(ErrorCode.VERIFICATION_WRONG, 400, "验证码错误或已过期");
        }

        User user = new User();
        user.setUserUuid("u_" + UUID.randomUUID().toString().replace("-", ""));
        user.setEmail(hasEmail ? email : null);
        user.setMobile(hasMobile ? mobile : null);
        user.setNickname(trim(req.getNickname()));
        user.setPasswordHash(encoder.encode(req.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        try {
            userRepository.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.CONFLICT, 409, "邮箱或手机号已被注册");
        }
        userResourceInitService.ensureRootFolder(user.getUserUuid());

        return new UserProfileVO(user.getUserUuid(), user.getNickname(), user.getAvatarUrl());
    }

    @Override
    @Transactional
    public LoginResponseVO login(LoginRequestDTO req) {
        String email = safeLower(req.getEmail());
        String mobile = trim(req.getMobile());
        User user = findUser(email, mobile);

        boolean hasPassword = !trim(req.getPassword()).isEmpty();
        boolean hasCode = !trim(req.getVerificationCode()).isEmpty();
        if (hasPassword == hasCode) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "请使用密码登录或验证码登录二选一");
        }
        if (hasPassword) {
            if (!encoder.matches(req.getPassword(), user.getPasswordHash())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, 401, "账号或密码错误");
            }
        } else {
            String channel = !email.isEmpty() ? "email" : "sms";
            if (!verificationService.verifyAndConsume(channel, email, mobile, req.getVerificationCode())) {
                throw new BizException(ErrorCode.VERIFICATION_WRONG, 400, "验证码错误或已过期");
            }
        }

        return issueToken(user, req.getClientType(), req.getDeviceId(), req.getDeviceName());
    }

    @Override
    @Transactional(readOnly = true)
    public TokenVO refresh(String refreshToken) {
        String tokenHash = sha256(trim(refreshToken));
        UserSession session = sessionRepository.findActiveByRefreshTokenHash(tokenHash);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, 410, "刷新令牌无效或已过期");
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, 410, "刷新令牌无效或已过期");
        }
        String accessToken = jwtTokenProvider.createAccessToken(
                session.getUserUuid(),
                session.getSessionUuid(),
                properties.getAccessTokenSeconds());
        return new TokenVO(accessToken, properties.getAccessTokenSeconds());
    }

    @Override
    @Transactional
    public void logout(String sessionId) {
        int affected = sessionRepository.revokeBySessionUuid(sessionId);
        if (affected == 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "会话已失效");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionVO> listSessions(String userUuid) {
        User user = userRepository.findByUserUuid(userUuid);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        List<UserSession> sessions = sessionRepository.listActiveByUserId(user.getId());
        List<SessionVO> out = new ArrayList<SessionVO>();
        for (UserSession s : sessions) {
            SessionVO item = new SessionVO();
            item.setSessionId(s.getSessionUuid());
            item.setDeviceId(Optional.ofNullable(s.getDeviceId()).orElse(""));
            item.setDeviceName(Optional.ofNullable(s.getDeviceName()).orElse(""));
            item.setClientType(s.getClientType());
            item.setExpiresAt(s.getExpiresAt().toString());
            out.add(item);
        }
        return out;
    }

    @Override
    @Transactional
    public void deleteSession(String userUuid, String sessionUuid) {
        UserSession s = sessionRepository.findActiveBySessionUuid(sessionUuid);
        if (s == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "会话不存在");
        }
        if (!Objects.equals(s.getUserUuid(), userUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "无权操作该会话");
        }
        sessionRepository.revokeBySessionUuid(sessionUuid);
    }

    private LoginResponseVO issueToken(User user, String clientType, String deviceId, String deviceName) {
        if (!"web".equals(clientType) && !"android".equals(clientType)) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "参数错误");
        }
        UserSession session = new UserSession();
        session.setSessionUuid("s_" + UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(user.getId());
        session.setUserUuid(user.getUserUuid());
        session.setClientType(clientType);
        session.setDeviceId(trim(deviceId).isEmpty() ? "device-" + UUID.randomUUID() : trim(deviceId));
        session.setDeviceName(trim(deviceName).isEmpty() ? clientType : trim(deviceName));
        String refreshToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        session.setRefreshTokenHash(sha256(refreshToken));
        session.setExpiresAt(LocalDateTime.now().plusSeconds(properties.getRefreshTokenSeconds()));
        session.setCreatedAt(LocalDateTime.now());
        sessionRepository.insert(session);

        String accessToken = jwtTokenProvider.createAccessToken(user.getUserUuid(), session.getSessionUuid(), properties.getAccessTokenSeconds());
        LoginResponseVO data = new LoginResponseVO();
        data.setAccessToken(accessToken);
        data.setRefreshToken(refreshToken);
        data.setExpiresIn(properties.getAccessTokenSeconds());
        data.setUser(new UserProfileVO(user.getUserUuid(), user.getNickname(), user.getAvatarUrl()));
        return data;
    }

    private User findUser(String email, String mobile) {
        if (!email.isEmpty() && mobile.isEmpty()) {
            User user = userRepository.findByEmail(email);
            if (user == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED, 401, "账号或密码错误");
            }
            return user;
        }
        if (!mobile.isEmpty() && email.isEmpty()) {
            User user = userRepository.findByMobile(mobile);
            if (user == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED, 401, "账号或密码错误");
            }
            return user;
        }
        throw new BizException(ErrorCode.INVALID_PARAM, 400, "参数错误");
    }

    private String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "内部错误");
        }
    }

    private String trim(String v) { return v == null ? "" : v.trim(); }
    private String safeLower(String v) { return trim(v).toLowerCase(); }

}

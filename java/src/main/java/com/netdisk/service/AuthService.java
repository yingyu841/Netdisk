package com.netdisk.service;

import com.netdisk.pojo.dto.LoginRequestDTO;
import com.netdisk.pojo.dto.RegisterRequestDTO;
import com.netdisk.pojo.vo.LoginResponseVO;
import com.netdisk.pojo.vo.SessionVO;
import com.netdisk.pojo.vo.TokenVO;
import com.netdisk.pojo.vo.UserProfileVO;

import java.util.List;

/**
 * 认证与会话服务接口。
 */
public interface AuthService {
    /**
     * 用户注册。
     *
     * @param req 注册参数
     * @return 用户资料
     */
    UserProfileVO register(RegisterRequestDTO req);

    /**
     * 用户登录（支持密码或验证码）。
     *
     * @param req 登录参数
     * @return 登录结果
     */
    LoginResponseVO login(LoginRequestDTO req);

    /**
     * 刷新访问令牌。
     *
     * @param refreshToken 刷新令牌
     * @return 新令牌信息
     */
    TokenVO refresh(String refreshToken);

    /**
     * 退出登录。
     *
     * @param sessionId 会话ID
     */
    void logout(String sessionId);

    /**
     * 查询用户全部有效会话。
     *
     * @param userUuid 用户业务ID
     * @return 会话列表
     */
    List<SessionVO> listSessions(String userUuid);

    /**
     * 删除指定会话。
     *
     * @param userUuid 用户业务ID
     * @param sessionUuid 会话业务ID
     */
    void deleteSession(String userUuid, String sessionUuid);
}

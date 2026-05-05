package com.netdisk.mapper;

import com.netdisk.pojo.entity.UserSession;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户会话数据访问接口。
 */
@Mapper
public interface UserSessionMapper {
    /**
     * 新增会话。
     *
     * @param session 会话实体
     * @return 影响行数
     */
    int insert(UserSession session);

    /**
     * 根据会话ID查询有效会话。
     *
     * @param sessionUuid 会话业务ID
     * @return 会话实体
     */
    UserSession findActiveBySessionUuid(String sessionUuid);

    /**
     * 根据刷新令牌哈希查询有效会话。
     *
     * @param refreshTokenHash 刷新令牌哈希
     * @return 会话实体
     */
    UserSession findActiveByRefreshTokenHash(String refreshTokenHash);

    /**
     * 查询用户下全部有效会话。
     *
     * @param userId 用户主键
     * @return 会话列表
     */
    List<UserSession> listActiveByUserId(Long userId);

    /**
     * 注销指定会话。
     *
     * @param sessionUuid 会话业务ID
     * @return 影响行数
     */
    int revokeBySessionUuid(String sessionUuid);
}

package com.netdisk.service;

import com.netdisk.pojo.entity.Resource;

import java.util.Map;

/**
 * 用户资源初始化服务。
 */
public interface UserResourceInitService {
    /**
     * 确保用户已初始化个人空间与根目录。
     *
     * @param userUuid 用户业务ID
     * @return 根目录
     */
    Resource ensureRootFolder(String userUuid);

    /**
     * 查询用户个人空间使用情况。
     *
     * @param userUuid 用户业务ID
     * @return 包含 usedBytes, quotaBytes 的 Map
     */
    Map<String, Object> getPersonalSpaceUsage(String userUuid);
}

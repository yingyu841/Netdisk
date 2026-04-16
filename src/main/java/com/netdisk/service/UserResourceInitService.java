package com.netdisk.service;

import com.netdisk.pojo.entity.ResourceEntity;

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
    ResourceEntity ensureRootFolder(String userUuid);
}

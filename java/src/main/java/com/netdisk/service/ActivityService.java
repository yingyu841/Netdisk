package com.netdisk.service;

import com.netdisk.pojo.vo.ActivityListResponseVO;

/**
 * 个人活动服务接口。
 */
public interface ActivityService {
    /**
     * 获取当前用户操作记录。
     *
     * @param userUuid 当前用户业务ID
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     * @return 操作记录列表
     */
    ActivityListResponseVO listActivities(String userUuid, Integer page, Integer pageSize);
}

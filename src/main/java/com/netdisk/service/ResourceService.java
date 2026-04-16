package com.netdisk.service;

import com.netdisk.pojo.vo.ResourceListResponseVO;
import com.netdisk.pojo.vo.ResourceTreeNodeVO;

import java.util.List;

/**
 * 资源服务接口。
 */
public interface ResourceService {
    /**
     * 获取目录下资源列表（文件+目录）。
     *
     * @param userUuid 当前用户业务ID
     * @param parentResourceUuid 父目录资源ID，为空时读取用户根目录
     * @param keyword 关键字（按名称过滤）
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     * @return 列表结果
     */
    ResourceListResponseVO listResources(String userUuid, String parentResourceUuid, String keyword, Integer page, Integer pageSize);

    /**
     * 获取用户目录树。
     *
     * @param userUuid 当前用户业务ID
     * @param parentResourceUuid 目录资源ID，为空时返回根目录树
     * @return 目录树
     */
    List<ResourceTreeNodeVO> getFolderTree(String userUuid, String parentResourceUuid);
}

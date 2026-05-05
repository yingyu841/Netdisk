package com.netdisk.service;

import com.netdisk.pojo.dto.CopyResourcesRequestDTO;
import com.netdisk.pojo.dto.CreateFolderRequestDTO;
import com.netdisk.pojo.dto.MoveResourcesRequestDTO;
import com.netdisk.pojo.dto.PurgeRecycleRequestDTO;
import com.netdisk.pojo.dto.RenameResourceRequestDTO;
import com.netdisk.pojo.vo.RecycleBinListResponseVO;
import com.netdisk.pojo.vo.ResourceListResponseVO;
import com.netdisk.pojo.vo.ResourceTreeNodeVO;

import java.util.List;
import java.util.Map;

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

    /**
     * 新建文件夹。
     *
     * @param userUuid 当前用户业务ID
     * @param request 请求参数
     * @return 新建目录信息
     */
    Map<String, Object> createFolder(String userUuid, CreateFolderRequestDTO request);

    /**
     * 批量移动资源到目标目录。
     *
     * @param userUuid 当前用户业务ID
     * @param request 请求参数
     * @return 处理结果
     */
    Map<String, Object> moveResources(String userUuid, MoveResourcesRequestDTO request);

    /**
     * 批量复制资源到目标目录。
     *
     * @param userUuid 当前用户业务ID
     * @param request 请求参数
     * @return 处理结果
     */
    Map<String, Object> copyResources(String userUuid, CopyResourcesRequestDTO request);

    /**
     * 重命名资源。
     *
     * @param userUuid 当前用户业务ID
     * @param resourceUuid 资源业务ID
     * @param request 请求参数
     * @return 重命名后的资源信息
     */
    Map<String, Object> renameResource(String userUuid, String resourceUuid, RenameResourceRequestDTO request);

    /**
     * 删除资源到回收站。
     *
     * @param userUuid 当前用户业务ID
     * @param resourceUuid 资源业务ID
     * @return 删除结果
     */
    Map<String, Object> deleteResource(String userUuid, String resourceUuid);

    /**
     * 从回收站恢复资源。
     *
     * @param userUuid 当前用户业务ID
     * @param resourceUuid 资源业务ID
     * @return 恢复后的资源信息
     */
    Map<String, Object> restoreResource(String userUuid, String resourceUuid);

    /**
     * 分页查询回收站（根级删除项）。
     */
    RecycleBinListResponseVO listRecycleBin(String userUuid, String keyword, Integer page, Integer pageSize);

    /**
     * 彻底删除回收站资源：purgeAll 清空回收站；否则按 resourceIds 批量彻底删除。
     */
    Map<String, Object> purgeRecycleResources(String userUuid, PurgeRecycleRequestDTO request);

    /**
     * 获取文件预览用临时访问 URL（本地存储 + 签名链接）。
     *
     * @param userUuid 当前用户业务ID
     * @param resourceUuid 文件资源业务ID
     * @param requestBaseUrl 当前请求的站点根 URL（含 context-path），用于拼接绝对地址
     * @return url、过期时间、文件名等
     */
    Map<String, Object> buildPreviewFileUrl(String userUuid, String resourceUuid, String requestBaseUrl);

    /**
     * 获取文件下载用临时访问 URL（本地存储 + 签名链接）。
     */
    Map<String, Object> buildDownloadFileUrl(String userUuid, String resourceUuid, String requestBaseUrl);
}

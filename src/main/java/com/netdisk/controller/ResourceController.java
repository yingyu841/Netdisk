package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.pojo.dto.CopyResourcesRequestDTO;
import com.netdisk.pojo.dto.CreateFolderRequestDTO;
import com.netdisk.pojo.dto.MoveResourcesRequestDTO;
import com.netdisk.pojo.dto.PurgeRecycleRequestDTO;
import com.netdisk.pojo.dto.RenameResourceRequestDTO;
import com.netdisk.pojo.vo.RecycleBinListResponseVO;
import com.netdisk.pojo.vo.ResourceListResponseVO;
import com.netdisk.pojo.vo.ResourceTreeNodeVO;
import com.netdisk.service.ResourceService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源与目录相关接口。
 */
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {
    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * 分页查询回收站（根级删除项）。
     */
    @GetMapping("/recycle")
    public ApiResponse<Map<String, Object>> recycleBin(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        RecycleBinListResponseVO list = resourceService.listRecycleBin(userUuid, keyword, page, pageSize);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", list.getTotal());
        data.put("items", list.getItems());
        return ApiResponse.ok(data, requestId(req));
    }

    /**
     * 彻底删除回收站资源：purgeAll 为 true 时清空回收站；否则按 resourceIds 批量或单条彻底删除。
     */
    @PostMapping("/recycle/purge")
    public ApiResponse<Map<String, Object>> purgeRecycle(
            @Valid @RequestBody PurgeRecycleRequestDTO request,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(resourceService.purgeRecycleResources(userUuid, request), requestId(req));
    }

    /**
     * 查询目录下资源列表（文件+目录）。
     */
    @GetMapping("")
    public ApiResponse<Map<String, Object>> resources(
            @RequestParam(required = false) String parentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        ResourceListResponseVO list = resourceService.listResources(userUuid, parentId, keyword, page, pageSize);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", list.getTotal());
        data.put("items", list.getItems());
        return ApiResponse.ok(data, requestId(req));
    }

    /**
     * 查询用户目录树。
     */
    @GetMapping("/tree")
    public ApiResponse<Map<String, Object>> directoryTree(
            @RequestParam(required = false) String parentId,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        List<ResourceTreeNodeVO> tree = resourceService.getFolderTree(userUuid, parentId);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("items", tree);
        return ApiResponse.ok(data, requestId(req));
    }

    /**
     * 新建文件夹。
     */
    @PostMapping("/folders")
    public ApiResponse<Map<String, Object>> createFolder(
            @Valid @RequestBody CreateFolderRequestDTO request,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(resourceService.createFolder(userUuid, request), requestId(req));
    }

    /**
     * 移动资源。
     */
    @PostMapping("/move")
    public ApiResponse<Map<String, Object>> move(
            @Valid @RequestBody MoveResourcesRequestDTO request,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(resourceService.moveResources(userUuid, request), requestId(req));
    }

    /**
     * 复制资源。
     */
    @PostMapping("/copy")
    public ApiResponse<Map<String, Object>> copy(
            @Valid @RequestBody CopyResourcesRequestDTO request,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(resourceService.copyResources(userUuid, request), requestId(req));
    }

    /**
     * 重命名资源。
     */
    @PatchMapping("/{resourceId}")
    public ApiResponse<Map<String, Object>> rename(
            @PathVariable String resourceId,
            @Valid @RequestBody RenameResourceRequestDTO request,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(resourceService.renameResource(userUuid, resourceId, request), requestId(req));
    }

    /**
     * 删除资源到回收站。
     */
    @DeleteMapping("/{resourceId}")
    public ApiResponse<Map<String, Object>> delete(
            @PathVariable String resourceId,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(resourceService.deleteResource(userUuid, resourceId), requestId(req));
    }

    /**
     * 从回收站恢复资源。
     */
    @PostMapping("/{resourceId}/restore")
    public ApiResponse<Map<String, Object>> restore(
            @PathVariable String resourceId,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(resourceService.restoreResource(userUuid, resourceId), requestId(req));
    }

    /**
     * 获取预览链接（本地存储：返回带签名的临时 file-access URL，便于 iframe/img 直接使用）。
     */
    @GetMapping("/{resourceId}/preview-url")
    public ApiResponse<Map<String, Object>> preview(
            @PathVariable String resourceId,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(resourceService.buildPreviewFileUrl(userUuid, resourceId, requestBaseUrl(req)), requestId(req));
    }

    /**
     * 获取下载链接（本地存储：返回带签名的临时 file-access URL）。
     */
    @GetMapping("/{resourceId}/download-url")
    public ApiResponse<Map<String, Object>> download(
            @PathVariable String resourceId,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(resourceService.buildDownloadFileUrl(userUuid, resourceId, requestBaseUrl(req)), requestId(req));
    }

    /**
     * 构造当前站点的根 URL（含 context-path），用于拼接绝对 file-access 地址。
     */
    private String requestBaseUrl(HttpServletRequest req) {
        String scheme = req.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.trim().isEmpty()) {
            scheme = req.getScheme();
        }
        String host = req.getHeader("X-Forwarded-Host");
        if (host == null || host.trim().isEmpty()) {
            host = req.getServerName() + ":" + req.getServerPort();
        } else {
            host = host.trim();
        }
        String ctx = req.getContextPath();
        if (ctx == null) {
            ctx = "";
        }
        return scheme + "://" + host + ctx;
    }

    /**
     * 从请求上下文读取请求ID。
     */
    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}

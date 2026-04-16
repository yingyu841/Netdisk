package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.pojo.vo.ResourceListResponseVO;
import com.netdisk.pojo.vo.ResourceTreeNodeVO;
import com.netdisk.service.ResourceService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
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
    public ApiResponse<Map<String, Object>> createFolder(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 移动资源。
     */
    @PostMapping("/move")
    public ApiResponse<Map<String, Object>> move(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 复制资源。
     */
    @PostMapping("/copy")
    public ApiResponse<Map<String, Object>> copy(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 重命名资源。
     */
    @PatchMapping("/{resourceId}")
    public ApiResponse<Map<String, Object>> rename(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 删除资源到回收站。
     */
    @DeleteMapping("/{resourceId}")
    public ApiResponse<Map<String, Object>> delete(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 从回收站恢复资源。
     */
    @PostMapping("/{resourceId}/restore")
    public ApiResponse<Map<String, Object>> restore(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 获取预览链接。
     */
    @GetMapping("/{resourceId}/preview-url")
    public ApiResponse<Map<String, Object>> preview(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 获取下载链接。
     */
    @GetMapping("/{resourceId}/download-url")
    public ApiResponse<Map<String, Object>> download(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 未实现接口的统一占位返回。
     */
    private ApiResponse<Map<String, Object>> todo(HttpServletRequest req) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("implemented", false);
        return ApiResponse.ok(data, requestId(req));
    }

    /**
     * 从请求上下文读取请求ID。
     */
    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}

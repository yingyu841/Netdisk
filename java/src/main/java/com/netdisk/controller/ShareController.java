package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.pojo.dto.CreateShareRequestDTO;
import com.netdisk.service.ShareService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

/**
 * 分享管理接口。
 */
@RestController
@RequestMapping("/api/v1/shares")
public class ShareController {
    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    /**
     * 创建分享。
     */
    @PostMapping("")
    public ApiResponse<Map<String, Object>> createShare(
            @Valid @RequestBody CreateShareRequestDTO request,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(shareService.createShare(userUuid, request), requestId(req));
    }

    /**
     * 查询分享列表。
     */
    @GetMapping("")
    public ApiResponse<Map<String, Object>> listShare(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(shareService.listShares(userUuid, page, pageSize), requestId(req));
    }

    /**
     * 撤销分享。
     */
    @PostMapping("/{shareId}/revoke")
    public ApiResponse<Map<String, Object>> revokeShare(
            @PathVariable String shareId,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(shareService.revokeShare(userUuid, shareId), requestId(req));
    }

    /**
     * 从请求上下文读取请求ID。
     */
    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}

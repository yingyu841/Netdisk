package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.pojo.dto.ShareVerifyRequestDTO;
import com.netdisk.service.SharePublicService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

/**
 * 公开分享访问接口。
 */
@RestController
@RequestMapping("/s/{shareCode}")
public class SharePublicController {
    private final SharePublicService sharePublicService;

    public SharePublicController(SharePublicService sharePublicService) {
        this.sharePublicService = sharePublicService;
    }

    /**
     * 获取公开分享元信息。
     */
    @GetMapping("/meta")
    public ApiResponse<Map<String, Object>> shareMeta(
            @PathVariable String shareCode,
            HttpServletRequest req) {
        return ApiResponse.ok(sharePublicService.shareMeta(shareCode), requestId(req));
    }

    /**
     * 校验分享访问口令。
     */
    @PostMapping("/verify")
    public ApiResponse<Map<String, Object>> shareVerify(
            @PathVariable String shareCode,
            @Valid @RequestBody ShareVerifyRequestDTO request,
            HttpServletRequest req) {
        return ApiResponse.ok(sharePublicService.verify(shareCode, request), requestId(req));
    }

    /**
     * 获取分享目录内容。
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> shareList(
            @PathVariable String shareCode,
            @RequestParam(required = false) String verifyToken,
            HttpServletRequest req) {
        return ApiResponse.ok(sharePublicService.list(shareCode, verifyToken), requestId(req));
    }

    /**
     * 获取分享下载链接。
     */
    @GetMapping("/download-url")
    public ApiResponse<Map<String, Object>> shareDownload(
            @PathVariable String shareCode,
            @RequestParam String resourceId,
            @RequestParam(required = false) String verifyToken,
            HttpServletRequest req) {
        return ApiResponse.ok(
                sharePublicService.downloadUrl(shareCode, resourceId, verifyToken, requestBaseUrl(req)),
                requestId(req));
    }

    /**
     * 构造当前站点根 URL（含 context-path）。
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

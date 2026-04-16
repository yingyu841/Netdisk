package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 分享管理接口。
 */
@RestController
@RequestMapping("/api/v1/shares")
public class ShareController {
    /**
     * 创建分享。
     */
    @PostMapping("")
    public ApiResponse<Map<String, Object>> createShare(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 查询分享列表。
     */
    @GetMapping("")
    public ApiResponse<Map<String, Object>> listShare(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 撤销分享。
     */
    @PostMapping("/{shareId}/revoke")
    public ApiResponse<Map<String, Object>> revokeShare(HttpServletRequest req) {
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

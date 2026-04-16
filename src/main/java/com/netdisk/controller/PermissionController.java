package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 资源权限接口。
 */
@RestController
public class PermissionController {
    /**
     * 查询资源 ACL。
     */
    @GetMapping("/api/v1/resources/{resourceId}/acl")
    public ApiResponse<Map<String, Object>> getAcl(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 更新资源 ACL。
     */
    @PutMapping("/api/v1/resources/{resourceId}/acl")
    public ApiResponse<Map<String, Object>> putAcl(HttpServletRequest req) {
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

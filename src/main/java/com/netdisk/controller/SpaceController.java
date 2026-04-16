package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 空间相关接口。
 */
@RestController
public class SpaceController {
    /**
     * 获取当前用户空间信息。
     */
    @GetMapping("/api/v1/spaces/current")
    public ApiResponse<Map<String, Object>> currentSpace(HttpServletRequest req) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", "");
        data.put("name", "");
        data.put("spaceType", "personal");
        data.put("role", "owner");
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

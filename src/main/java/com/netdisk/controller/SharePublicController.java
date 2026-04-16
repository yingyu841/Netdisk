package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公开分享访问接口。
 */
@RestController
@RequestMapping("/s/{shareCode}")
public class SharePublicController {
    /**
     * 获取公开分享元信息。
     */
    @GetMapping("/meta")
    public ApiResponse<Map<String, Object>> shareMeta(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 校验分享访问口令。
     */
    @PostMapping("/verify")
    public ApiResponse<Map<String, Object>> shareVerify(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 获取分享目录内容。
     */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> shareList(HttpServletRequest req) {
        return todo(req);
    }

    /**
     * 获取分享下载链接。
     */
    @GetMapping("/download-url")
    public ApiResponse<Map<String, Object>> shareDownload(HttpServletRequest req) {
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

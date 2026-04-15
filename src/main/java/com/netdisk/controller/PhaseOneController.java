package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PhaseOneController {

    @GetMapping("/healthz")
    public Map<String, String> health() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("status", "ok");
        return map;
    }

    @GetMapping("/api/v1/spaces/current")
    public ApiResponse<Map<String, Object>> currentSpace(HttpServletRequest req) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", "");
        data.put("name", "");
        data.put("spaceType", "personal");
        data.put("role", "owner");
        return ApiResponse.ok(data, requestId(req));
    }

    @GetMapping("/api/v1/resources")
    public ApiResponse<Map<String, Object>> resources(HttpServletRequest req) { return todo(req); }
    @PostMapping("/api/v1/resources/folders")
    public ApiResponse<Map<String, Object>> createFolder(HttpServletRequest req) { return todo(req); }
    @PostMapping("/api/v1/resources/move")
    public ApiResponse<Map<String, Object>> move(HttpServletRequest req) { return todo(req); }
    @PostMapping("/api/v1/resources/copy")
    public ApiResponse<Map<String, Object>> copy(HttpServletRequest req) { return todo(req); }
    @PatchMapping("/api/v1/resources/{resourceId}")
    public ApiResponse<Map<String, Object>> rename(HttpServletRequest req) { return todo(req); }
    @DeleteMapping("/api/v1/resources/{resourceId}")
    public ApiResponse<Map<String, Object>> delete(HttpServletRequest req) { return todo(req); }
    @PostMapping("/api/v1/resources/{resourceId}/restore")
    public ApiResponse<Map<String, Object>> restore(HttpServletRequest req) { return todo(req); }
    @GetMapping("/api/v1/resources/{resourceId}/preview-url")
    public ApiResponse<Map<String, Object>> preview(HttpServletRequest req) { return todo(req); }
    @GetMapping("/api/v1/resources/{resourceId}/download-url")
    public ApiResponse<Map<String, Object>> download(HttpServletRequest req) { return todo(req); }

    @PostMapping("/api/v1/uploads/init")
    public ApiResponse<Map<String, Object>> uploadInit(HttpServletRequest req) { return todo(req); }
    @PutMapping("/api/v1/uploads/{uploadId}/parts/{partNumber}")
    public ApiResponse<Map<String, Object>> uploadPart(HttpServletRequest req) { return todo(req); }
    @PostMapping("/api/v1/uploads/{uploadId}/complete")
    public ApiResponse<Map<String, Object>> uploadComplete(HttpServletRequest req) { return todo(req); }
    @GetMapping("/api/v1/uploads/{uploadId}")
    public ApiResponse<Map<String, Object>> uploadStatus(HttpServletRequest req) { return todo(req); }
    @DeleteMapping("/api/v1/uploads/{uploadId}")
    public ApiResponse<Map<String, Object>> uploadCancel(HttpServletRequest req) { return todo(req); }

    @PostMapping("/api/v1/shares")
    public ApiResponse<Map<String, Object>> createShare(HttpServletRequest req) { return todo(req); }
    @GetMapping("/api/v1/shares")
    public ApiResponse<Map<String, Object>> listShare(HttpServletRequest req) { return todo(req); }
    @PostMapping("/api/v1/shares/{shareId}/revoke")
    public ApiResponse<Map<String, Object>> revokeShare(HttpServletRequest req) { return todo(req); }

    @GetMapping("/s/{shareCode}/meta")
    public ApiResponse<Map<String, Object>> shareMeta(HttpServletRequest req) { return todo(req); }
    @PostMapping("/s/{shareCode}/verify")
    public ApiResponse<Map<String, Object>> shareVerify(HttpServletRequest req) { return todo(req); }
    @GetMapping("/s/{shareCode}/list")
    public ApiResponse<Map<String, Object>> shareList(HttpServletRequest req) { return todo(req); }
    @GetMapping("/s/{shareCode}/download-url")
    public ApiResponse<Map<String, Object>> shareDownload(HttpServletRequest req) { return todo(req); }

    @GetMapping("/api/v1/resources/{resourceId}/acl")
    public ApiResponse<Map<String, Object>> getAcl(HttpServletRequest req) { return todo(req); }
    @PutMapping("/api/v1/resources/{resourceId}/acl")
    public ApiResponse<Map<String, Object>> putAcl(HttpServletRequest req) { return todo(req); }

    @GetMapping("/api/v1/me/activities")
    public ApiResponse<Map<String, Object>> activities(HttpServletRequest req) { return todo(req); }

    private ApiResponse<Map<String, Object>> todo(HttpServletRequest req) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("implemented", false);
        return ApiResponse.ok(data, requestId(req));
    }

    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}

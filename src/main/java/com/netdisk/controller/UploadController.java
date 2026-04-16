package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.pojo.dto.UploadCompleteRequest;
import com.netdisk.pojo.dto.UploadInitRequest;
import com.netdisk.service.UploadService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上传相关接口。
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {
    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /**
     * 初始化上传会话。
     */
    @PostMapping("/init")
    public ApiResponse<Map<String, Object>> uploadInit(@Valid @RequestBody UploadInitRequest request, HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(uploadService.init(userUuid, request), requestId(req));
    }

    /**
     * 上传分片。
     */
    @PutMapping("/{uploadId}/parts/{partNumber}")
    public ApiResponse<Map<String, Object>> uploadPart(
            @PathVariable String uploadId,
            @PathVariable Integer partNumber,
            HttpServletRequest req) throws IOException {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(uploadService.uploadPart(userUuid, uploadId, partNumber, req.getInputStream()), requestId(req));
    }

    /**
     * 完成上传。
     */
    @PostMapping("/{uploadId}/complete")
    public ApiResponse<Map<String, Object>> uploadComplete(
            @PathVariable String uploadId,
            @Valid @RequestBody UploadCompleteRequest request,
            HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(uploadService.complete(userUuid, uploadId, request), requestId(req));
    }

    /**
     * 查询上传状态。
     */
    @GetMapping("/{uploadId}")
    public ApiResponse<Map<String, Object>> uploadStatus(@PathVariable String uploadId, HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(uploadService.status(userUuid, uploadId), requestId(req));
    }

    /**
     * 取消上传会话。
     */
    @DeleteMapping("/{uploadId}")
    public ApiResponse<Map<String, Object>> uploadCancel(@PathVariable String uploadId, HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        uploadService.cancel(userUuid, uploadId);
        return ApiResponse.ok(new LinkedHashMap<String, Object>(), requestId(req));
    }

    /**
     * 从请求上下文读取请求ID。
     */
    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}

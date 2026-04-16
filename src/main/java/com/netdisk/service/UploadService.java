package com.netdisk.service;

import com.netdisk.pojo.dto.UploadCompleteRequest;
import com.netdisk.pojo.dto.UploadInitRequest;

import java.io.InputStream;
import java.util.Map;

/**
 * 上传服务接口。
 */
public interface UploadService {
    Map<String, Object> init(String userUuid, UploadInitRequest request);

    Map<String, Object> uploadPart(String userUuid, String uploadId, Integer partNumber, InputStream stream);

    Map<String, Object> complete(String userUuid, String uploadId, UploadCompleteRequest request);

    Map<String, Object> status(String userUuid, String uploadId);

    void cancel(String userUuid, String uploadId);
}

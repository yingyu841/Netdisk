package com.netdisk.service;

import com.netdisk.pojo.dto.ShareVerifyRequestDTO;

import java.util.Map;

/**
 * 公开分享访问服务。
 */
public interface SharePublicService {
    Map<String, Object> shareMeta(String shareCode);

    Map<String, Object> verify(String shareCode, ShareVerifyRequestDTO request);

    Map<String, Object> list(String shareCode, String verifyToken);

    Map<String, Object> downloadUrl(String shareCode, String resourceId, String verifyToken, String requestBaseUrl);
}

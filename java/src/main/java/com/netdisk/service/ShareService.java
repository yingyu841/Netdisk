package com.netdisk.service;

import com.netdisk.pojo.dto.CreateShareRequestDTO;

import java.util.Map;

/**
 * 分享管理服务。
 */
public interface ShareService {
    Map<String, Object> createShare(String userUuid, CreateShareRequestDTO request);

    Map<String, Object> listShares(String userUuid, Integer page, Integer pageSize);

    Map<String, Object> revokeShare(String userUuid, String shareId);
}

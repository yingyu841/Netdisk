package com.netdisk.service.impl;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.config.AppProperties;
import com.netdisk.mapper.ShareMapper;
import com.netdisk.mapper.ShareResourceMapper;
import com.netdisk.mapper.UserMapper;
import com.netdisk.pojo.dto.ShareVerifyRequestDTO;
import com.netdisk.pojo.entity.Resource;
import com.netdisk.pojo.entity.Share;
import com.netdisk.pojo.entity.User;
import com.netdisk.security.ShareVerifyTokenSigner;
import com.netdisk.service.ResourceService;
import com.netdisk.service.SharePublicService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 公开分享访问服务实现。
 */
@Service
public class SharePublicServiceImpl implements SharePublicService {
    private final ShareMapper shareMapper;
    private final ShareResourceMapper shareResourceMapper;
    private final UserMapper userMapper;
    private final ResourceService resourceService;
    private final AppProperties appProperties;

    public SharePublicServiceImpl(
            ShareMapper shareMapper,
            ShareResourceMapper shareResourceMapper,
            UserMapper userMapper,
            ResourceService resourceService,
            AppProperties appProperties) {
        this.shareMapper = shareMapper;
        this.shareResourceMapper = shareResourceMapper;
        this.userMapper = userMapper;
        this.resourceService = resourceService;
        this.appProperties = appProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> shareMeta(String shareCode) {
        Share share = requireActiveShare(shareCode);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("shareCode", share.getShareUuid());
        data.put("title", share.getTitle());
        data.put("status", share.getStatus());
        data.put("needCode", !trim(share.getAccessCodeHash()).isEmpty());
        data.put("allowPreview", share.getAllowPreview() != null && share.getAllowPreview().intValue() == 1);
        data.put("allowDownload", share.getAllowDownload() != null && share.getAllowDownload().intValue() == 1);
        data.put("maxAccessCount", share.getMaxAccessCount());
        data.put("currentAccessCount", share.getCurrentAccessCount() == null ? 0 : share.getCurrentAccessCount().intValue());
        data.put("expiredAt", share.getExpiredAt() == null ? null : share.getExpiredAt().toString());
        data.put("resourceCount", shareResourceMapper.countByShareId(share.getId()));
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> verify(String shareCode, ShareVerifyRequestDTO request) {
        Share share = requireActiveShare(shareCode);
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "请求体不合法");
        }
        String hash = trim(share.getAccessCodeHash());
        if (!hash.isEmpty()) {
            String code = trim(request.getCode());
            if (code.isEmpty()) {
                throw new BizException(ErrorCode.INVALID_PARAM, 400, "code必填");
            }
            String actual = sha256Hex(code);
            if (!actual.equalsIgnoreCase(hash)) {
                throw new BizException(ErrorCode.VERIFICATION_WRONG, 400, "提取码错误");
            }
        }

        long ttl = appProperties.getFileAccessTtlSeconds() <= 0L ? 3600L : appProperties.getFileAccessTtlSeconds();
        String token = ShareVerifyTokenSigner.issue(appProperties.resolveFileAccessSecret(), share.getShareUuid(), ttl);
        String expiresAt = Instant.now().plusSeconds(ttl).toString();

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("verifyToken", token);
        data.put("expiresAt", expiresAt);
        data.put("ttlSeconds", ttl);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> list(String shareCode, String verifyToken) {
        Share share = requireActiveShare(shareCode);
        ensureTokenIfNeeded(share, verifyToken);
        consumeAccess(share);
        List<Resource> rows = shareResourceMapper.listActiveResourcesByShareId(share.getId());
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Resource row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", row.getResourceUuid());
            item.put("parentId", null);
            item.put("type", row.getResourceType());
            item.put("name", row.getName());
            item.put("size", row.getSizeBytes() == null ? 0L : row.getSizeBytes().longValue());
            item.put("extension", row.getExtension());
            item.put("updatedAt", row.getUpdatedAt() == null ? null : row.getUpdatedAt().toString());
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", items.size());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> downloadUrl(String shareCode, String resourceId, String verifyToken, String requestBaseUrl) {
        Share share = requireActiveShare(shareCode);
        if (share.getAllowDownload() == null || share.getAllowDownload().intValue() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "当前分享不允许下载");
        }
        ensureTokenIfNeeded(share, verifyToken);
        Resource resource = shareResourceMapper.findActiveResourceInShare(share.getId(), trim(resourceId));
        if (resource == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "分享资源不存在");
        }
        if (!"file".equals(trim(resource.getResourceType()))) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "仅支持下载文件资源");
        }
        User owner = userMapper.findById(resource.getOwnerUserId());
        if (owner == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "资源所属用户不存在");
        }
        consumeAccess(share);
        return resourceService.buildDownloadFileUrl(owner.getUserUuid(), resource.getResourceUuid(), requestBaseUrl);
    }

    private Share requireActiveShare(String shareCode) {
        String normalizedCode = trim(shareCode);
        if (normalizedCode.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "shareCode不能为空");
        }
        Share share = shareMapper.findByShareUuid(normalizedCode);
        if (share == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "分享不存在");
        }
        if (!"active".equals(trim(share.getStatus()))) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "分享不存在");
        }
        LocalDateTime expiredAt = share.getExpiredAt();
        if (expiredAt != null && !expiredAt.isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.CONFLICT, 409, "分享已过期");
        }
        Integer max = share.getMaxAccessCount();
        Integer current = share.getCurrentAccessCount();
        if (max != null && current != null && current.intValue() >= max.intValue()) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "分享访问次数已达上限");
        }
        return share;
    }

    private void ensureTokenIfNeeded(Share share, String verifyToken) {
        if (trim(share.getAccessCodeHash()).isEmpty()) {
            return;
        }
        boolean ok = ShareVerifyTokenSigner.verify(
                appProperties.resolveFileAccessSecret(),
                share.getShareUuid(),
                verifyToken);
        if (!ok) {
            throw new BizException(ErrorCode.VERIFICATION_WRONG, 400, "请先校验提取码");
        }
    }

    private void consumeAccess(Share share) {
        int updated = shareMapper.tryConsumeAccess(share.getId());
        if (updated <= 0) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "分享访问次数已达上限或已失效");
        }
    }

    private String sha256Hex(String plain) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(trim(plain).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "提取码校验失败");
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

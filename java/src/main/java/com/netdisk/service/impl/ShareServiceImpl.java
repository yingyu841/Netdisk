package com.netdisk.service.impl;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.mapper.ResourceMapper;
import com.netdisk.mapper.ShareMapper;
import com.netdisk.mapper.ShareResourceMapper;
import com.netdisk.mapper.UserMapper;
import com.netdisk.pojo.dto.CreateShareRequestDTO;
import com.netdisk.pojo.entity.Resource;
import com.netdisk.pojo.entity.Share;
import com.netdisk.pojo.entity.User;
import com.netdisk.service.ShareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 分享管理服务实现。
 */
@Service
public class ShareServiceImpl implements ShareService {
    private final UserMapper userMapper;
    private final ResourceMapper resourceMapper;
    private final ShareMapper shareMapper;
    private final ShareResourceMapper shareResourceMapper;

    public ShareServiceImpl(
            UserMapper userMapper,
            ResourceMapper resourceMapper,
            ShareMapper shareMapper,
            ShareResourceMapper shareResourceMapper) {
        this.userMapper = userMapper;
        this.resourceMapper = resourceMapper;
        this.shareMapper = shareMapper;
        this.shareResourceMapper = shareResourceMapper;
    }

    @Override
    @Transactional
    public Map<String, Object> createShare(String userUuid, CreateShareRequestDTO request) {
        User user = requireUser(userUuid);
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "请求体不合法");
        }
        Set<String> resourceUuids = normalizeResourceIds(request.getResourceIds());
        if (resourceUuids.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "resourceIds不能为空");
        }
        if (request.getExpiredAt() != null && !request.getExpiredAt().isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "expiredAt必须晚于当前时间");
        }

        List<Resource> resources = new ArrayList<Resource>();
        Long spaceId = null;
        List<Long> resourceDbIds = new ArrayList<Long>();
        for (String resourceUuid : resourceUuids) {
            Resource entity = resourceMapper.findActiveByResourceUuid(user.getUserUuid(), resourceUuid);
            if (entity == null) {
                throw new BizException(ErrorCode.NOT_FOUND, 404, "资源不存在");
            }
            if (spaceId == null) {
                spaceId = entity.getSpaceId();
            } else if (!spaceId.equals(entity.getSpaceId())) {
                throw new BizException(ErrorCode.INVALID_PARAM, 400, "仅支持同一空间下资源分享");
            }
            resources.add(entity);
            resourceDbIds.add(entity.getId());
        }

        boolean needCode = Boolean.TRUE.equals(request.getNeedCode());
        String plainCode = trim(request.getCode());
        String accessCodeHash = null;
        if (needCode) {
            if (plainCode.isEmpty()) {
                throw new BizException(ErrorCode.INVALID_PARAM, 400, "needCode=true时code必填");
            }
            accessCodeHash = sha256Hex(plainCode);
        }

        Share share = new Share();
        share.setShareUuid(UUID.randomUUID().toString());
        share.setSpaceId(spaceId);
        share.setCreatorUserId(user.getId());
        share.setShareType("link");
        share.setTitle(resolveShareTitle(resources));
        share.setAccessCodeHash(accessCodeHash);
        share.setAllowPreview(Boolean.FALSE.equals(request.getAllowPreview()) ? 0 : 1);
        share.setAllowDownload(Boolean.FALSE.equals(request.getAllowDownload()) ? 0 : 1);
        share.setMaxAccessCount(request.getMaxAccessCount());
        share.setCurrentAccessCount(0);
        share.setExpiredAt(request.getExpiredAt());
        share.setStatus("active");
        share.setRevokedAt(null);
        shareMapper.insert(share);
        shareResourceMapper.insertBatch(share.getId(), resourceDbIds);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", share.getShareUuid());
        data.put("shareCode", share.getShareUuid());
        data.put("title", share.getTitle());
        data.put("url", "/s/" + share.getShareUuid());
        data.put("needCode", needCode);
        data.put("allowPreview", share.getAllowPreview() != null && share.getAllowPreview().intValue() == 1);
        data.put("allowDownload", share.getAllowDownload() != null && share.getAllowDownload().intValue() == 1);
        data.put("maxAccessCount", share.getMaxAccessCount());
        data.put("currentAccessCount", share.getCurrentAccessCount());
        data.put("expiredAt", share.getExpiredAt() == null ? null : share.getExpiredAt().toString());
        data.put("resourceCount", resourceDbIds.size());
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listShares(String userUuid, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        int safePage = page == null ? 1 : page.intValue();
        int safePageSize = pageSize == null ? 20 : pageSize.intValue();
        if (safePage < 1 || safePageSize < 1 || safePageSize > 200) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "分页参数不合法");
        }
        int offset = (safePage - 1) * safePageSize;
        Long total = shareMapper.countByCreatorUserId(user.getId());
        List<Share> rows = shareMapper.listByCreatorUserId(user.getId(), offset, safePageSize);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Share row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", row.getShareUuid());
            item.put("shareCode", row.getShareUuid());
            item.put("title", row.getTitle());
            item.put("status", row.getStatus());
            item.put("needCode", !trim(row.getAccessCodeHash()).isEmpty());
            item.put("allowPreview", row.getAllowPreview() != null && row.getAllowPreview().intValue() == 1);
            item.put("allowDownload", row.getAllowDownload() != null && row.getAllowDownload().intValue() == 1);
            item.put("maxAccessCount", row.getMaxAccessCount());
            item.put("currentAccessCount", row.getCurrentAccessCount() == null ? 0 : row.getCurrentAccessCount().intValue());
            item.put("expiredAt", row.getExpiredAt() == null ? null : row.getExpiredAt().toString());
            item.put("createdAt", row.getCreatedAt() == null ? null : row.getCreatedAt().toString());
            item.put("resourceCount", shareResourceMapper.countByShareId(row.getId()));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> revokeShare(String userUuid, String shareId) {
        User user = requireUser(userUuid);
        Share share = shareMapper.findByShareUuid(trim(shareId));
        if (share == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "分享不存在");
        }
        if (!user.getId().equals(share.getCreatorUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "无权操作该分享");
        }
        boolean alreadyRevoked = "revoked".equalsIgnoreCase(trim(share.getStatus()));
        if (!alreadyRevoked) {
            shareMapper.revokeByShareUuid(share.getShareUuid(), user.getId());
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", share.getShareUuid());
        data.put("revoked", true);
        data.put("alreadyRevoked", alreadyRevoked);
        return data;
    }

    private User requireUser(String userUuid) {
        String normalized = trim(userUuid);
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        User user = userMapper.findByUserUuid(normalized);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        return user;
    }

    private String resolveShareTitle(List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            return "分享";
        }
        if (resources.size() == 1) {
            return trim(resources.get(0).getName());
        }
        return resources.size() + "个项目分享";
    }

    private Set<String> normalizeResourceIds(List<String> input) {
        Set<String> out = new LinkedHashSet<String>();
        if (input == null) {
            return out;
        }
        for (String value : input) {
            String id = trim(value);
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return out;
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
            throw new BizException(ErrorCode.INTERNAL, 500, "生成提取码失败");
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

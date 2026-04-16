package com.netdisk.service.impl;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.mapper.ResourceRepository;
import com.netdisk.pojo.entity.ResourceEntity;
import com.netdisk.pojo.vo.ResourceItemVO;
import com.netdisk.pojo.vo.ResourceListResponseVO;
import com.netdisk.pojo.vo.ResourceTreeNodeVO;
import com.netdisk.service.ResourceService;
import com.netdisk.service.UserResourceInitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * 资源服务实现。
 */
@Service
public class ResourceServiceImpl implements ResourceService {
    private final ResourceRepository resourceRepository;
    private final UserResourceInitService userResourceInitService;

    public ResourceServiceImpl(ResourceRepository resourceRepository, UserResourceInitService userResourceInitService) {
        this.resourceRepository = resourceRepository;
        this.userResourceInitService = userResourceInitService;
    }

    @Override
    @Transactional
    public ResourceListResponseVO listResources(String userUuid, String parentResourceUuid, String keyword, Integer page, Integer pageSize) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        int safePage = page == null ? 1 : page.intValue();
        int safePageSize = pageSize == null ? 20 : pageSize.intValue();
        if (safePage < 1 || safePageSize < 1 || safePageSize > 200) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "分页参数不合法");
        }

        ResourceEntity parent = resolveParentFolder(normalizedUserUuid, parentResourceUuid);
        int offset = (safePage - 1) * safePageSize;
        String keywordNormalized = normalizeKeyword(keyword);
        Long total = resourceRepository.countByParent(normalizedUserUuid, parent.getId(), keywordNormalized);
        List<ResourceEntity> entities = resourceRepository.listByParent(
                normalizedUserUuid,
                parent.getId(),
                keywordNormalized,
                offset,
                safePageSize);

        List<ResourceItemVO> items = new ArrayList<ResourceItemVO>();
        for (ResourceEntity entity : entities) {
            ResourceItemVO item = new ResourceItemVO();
            item.setId(entity.getResourceUuid());
            item.setParentId(parent.getResourceUuid());
            item.setType(entity.getResourceType());
            item.setName(entity.getName());
            item.setSize(entity.getSizeBytes() == null ? 0L : entity.getSizeBytes());
            item.setExtension(entity.getExtension());
            item.setUpdatedAt(entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString());
            items.add(item);
        }

        ResourceListResponseVO out = new ResourceListResponseVO();
        out.setTotal(total == null ? 0L : total.longValue());
        out.setItems(items);
        return out;
    }

    @Override
    @Transactional
    public List<ResourceTreeNodeVO> getFolderTree(String userUuid, String parentResourceUuid) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        userResourceInitService.ensureRootFolder(normalizedUserUuid);
        String normalizedParentUuid = trim(parentResourceUuid);
        List<ResourceEntity> folders = resourceRepository.listFoldersByUserUuid(normalizedUserUuid);
        Map<Long, ResourceEntity> entityById = new LinkedHashMap<Long, ResourceEntity>();
        Map<String, ResourceEntity> entityByUuid = new LinkedHashMap<String, ResourceEntity>();
        Map<Long, ResourceTreeNodeVO> nodeById = new LinkedHashMap<Long, ResourceTreeNodeVO>();
        for (ResourceEntity folder : folders) {
            entityById.put(folder.getId(), folder);
            entityByUuid.put(folder.getResourceUuid(), folder);

            ResourceTreeNodeVO node = new ResourceTreeNodeVO();
            node.setId(folder.getResourceUuid());
            node.setName(folder.getName());
            nodeById.put(folder.getId(), node);
        }

        for (ResourceEntity folder : folders) {
            ResourceTreeNodeVO currentNode = nodeById.get(folder.getId());
            Long parentDbId = folder.getParentId();
            if (parentDbId == null) {
                continue;
            }
            ResourceEntity parentEntity = entityById.get(parentDbId);
            if (parentEntity != null) {
                currentNode.setParentId(parentEntity.getResourceUuid());
                ResourceTreeNodeVO parentNode = nodeById.get(parentDbId);
                parentNode.getChildren().add(currentNode);
            }
        }

        if (!normalizedParentUuid.isEmpty()) {
            ResourceEntity root = entityByUuid.get(normalizedParentUuid);
            if (root == null) {
                throw new BizException(ErrorCode.NOT_FOUND, 404, "目录不存在");
            }
            List<ResourceTreeNodeVO> out = new ArrayList<ResourceTreeNodeVO>();
            out.add(nodeById.get(root.getId()));
            return out;
        }

        List<ResourceTreeNodeVO> roots = new ArrayList<ResourceTreeNodeVO>();
        for (ResourceEntity folder : folders) {
            Long parentDbId = folder.getParentId();
            if (parentDbId == null || !entityById.containsKey(parentDbId)) {
                roots.add(nodeById.get(folder.getId()));
            }
        }
        return roots;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private ResourceEntity resolveParentFolder(String userUuid, String parentResourceUuid) {
        String normalizedParentUuid = trim(parentResourceUuid);
        if (normalizedParentUuid.isEmpty()) {
            return userResourceInitService.ensureRootFolder(userUuid);
        }
        ResourceEntity folder = resourceRepository.findFolderByResourceUuid(userUuid, normalizedParentUuid);
        if (folder == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "目录不存在");
        }
        return folder;
    }

    private String normalizeKeyword(String keyword) {
        String v = trim(keyword);
        return v.isEmpty() ? "" : v.toLowerCase(Locale.ROOT);
    }
}

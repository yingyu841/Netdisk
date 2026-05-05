package com.netdisk.service.impl;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.mapper.ResourceMapper;
import com.netdisk.mapper.StorageObjectMapper;
import com.netdisk.mapper.UserMapper;
import com.netdisk.config.AppProperties;
import com.netdisk.security.FileAccessSigner;
import com.netdisk.pojo.dto.CopyResourcesRequestDTO;
import com.netdisk.pojo.dto.CreateFolderRequestDTO;
import com.netdisk.pojo.dto.MoveResourcesRequestDTO;
import com.netdisk.pojo.dto.PurgeRecycleRequestDTO;
import com.netdisk.pojo.dto.RenameResourceRequestDTO;
import com.netdisk.pojo.entity.RecycleBinRow;
import com.netdisk.pojo.entity.RecycleEntry;
import com.netdisk.pojo.entity.Resource;
import com.netdisk.pojo.entity.StorageObject;
import com.netdisk.pojo.entity.User;
import com.netdisk.pojo.vo.RecycleBinItemVO;
import com.netdisk.pojo.vo.RecycleBinListResponseVO;
import com.netdisk.pojo.vo.ResourceItemVO;
import com.netdisk.pojo.vo.ResourceListResponseVO;
import com.netdisk.pojo.vo.ResourceTreeNodeVO;
import com.netdisk.service.ResourceService;
import com.netdisk.service.UserResourceInitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;

/**
 * 资源服务实现。
 */
@Service
public class ResourceServiceImpl implements ResourceService {
    private final ResourceMapper resourceRepository;
    private final UserResourceInitService userResourceInitService;
    private final UserMapper userMapper;
    private final StorageObjectMapper storageObjectRepository;
    private final AppProperties appProperties;

    public ResourceServiceImpl(
            ResourceMapper resourceRepository,
            UserResourceInitService userResourceInitService,
            UserMapper userMapper,
            StorageObjectMapper storageObjectRepository,
            AppProperties appProperties) {
        this.resourceRepository = resourceRepository;
        this.userResourceInitService = userResourceInitService;
        this.userMapper = userMapper;
        this.storageObjectRepository = storageObjectRepository;
        this.appProperties = appProperties;
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

        Resource parent = resolveParentFolder(normalizedUserUuid, parentResourceUuid);
        int offset = (safePage - 1) * safePageSize;
        String keywordNormalized = normalizeKeyword(keyword);
        Long total = resourceRepository.countByParent(normalizedUserUuid, parent.getId(), keywordNormalized);
        List<Resource> entities = resourceRepository.listByParent(
                normalizedUserUuid,
                parent.getId(),
                keywordNormalized,
                offset,
                safePageSize);

        List<ResourceItemVO> items = new ArrayList<ResourceItemVO>();
        for (Resource entity : entities) {
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
        List<Resource> folders = resourceRepository.listFoldersByUserUuid(normalizedUserUuid);
        Map<Long, Resource> entityById = new LinkedHashMap<Long, Resource>();
        Map<String, Resource> entityByUuid = new LinkedHashMap<String, Resource>();
        Map<Long, ResourceTreeNodeVO> nodeById = new LinkedHashMap<Long, ResourceTreeNodeVO>();
        for (Resource folder : folders) {
            entityById.put(folder.getId(), folder);
            entityByUuid.put(folder.getResourceUuid(), folder);

            ResourceTreeNodeVO node = new ResourceTreeNodeVO();
            node.setId(folder.getResourceUuid());
            node.setName(folder.getName());
            nodeById.put(folder.getId(), node);
        }

        for (Resource folder : folders) {
            ResourceTreeNodeVO currentNode = nodeById.get(folder.getId());
            Long parentDbId = folder.getParentId();
            if (parentDbId == null) {
                continue;
            }
            Resource parentEntity = entityById.get(parentDbId);
            if (parentEntity != null) {
                currentNode.setParentId(parentEntity.getResourceUuid());
                ResourceTreeNodeVO parentNode = nodeById.get(parentDbId);
                parentNode.getChildren().add(currentNode);
            }
        }

        if (!normalizedParentUuid.isEmpty()) {
            Resource root = entityByUuid.get(normalizedParentUuid);
            if (root == null) {
                throw new BizException(ErrorCode.NOT_FOUND, 404, "目录不存在");
            }
            List<ResourceTreeNodeVO> out = new ArrayList<ResourceTreeNodeVO>();
            out.add(nodeById.get(root.getId()));
            return out;
        }

        List<ResourceTreeNodeVO> roots = new ArrayList<ResourceTreeNodeVO>();
        for (Resource folder : folders) {
            Long parentDbId = folder.getParentId();
            if (parentDbId == null || !entityById.containsKey(parentDbId)) {
                roots.add(nodeById.get(folder.getId()));
            }
        }
        return roots;
    }

    @Override
    @Transactional
    public Map<String, Object> createFolder(String userUuid, CreateFolderRequestDTO request) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "请求体不合法");
        }
        Resource parent = resolveParentFolder(normalizedUserUuid, request.getParentId());
        String folderName = resolveUniqueName(parent.getId(), request.getName(), null, "同名文件夹过多，请更换名称");

        Resource folder = new Resource();
        folder.setResourceUuid(UUID.randomUUID().toString());
        folder.setSpaceId(parent.getSpaceId());
        folder.setParentId(parent.getId());
        folder.setName(folderName);
        folder.setNameNormalized(folderName.toLowerCase(Locale.ROOT));
        folder.setPathCache(buildPath(parent.getPathCache(), folderName));
        folder.setOwnerUserId(parent.getOwnerUserId());
        resourceRepository.insertFolderResource(folder);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", folder.getResourceUuid());
        data.put("parentId", parent.getResourceUuid());
        data.put("name", folder.getName());
        data.put("type", "folder");
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> moveResources(String userUuid, MoveResourcesRequestDTO request) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "请求体不合法");
        }
        Resource targetParent = resolveParentFolder(normalizedUserUuid, request.getTargetParentId());
        Set<String> resourceUuids = normalizeResourceIds(request.getResourceIds());
        if (resourceUuids.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "resourceIds不能为空");
        }

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (String resourceUuid : resourceUuids) {
            Resource entity = requireActiveResource(normalizedUserUuid, resourceUuid);
            if (entity.getParentId() == null) {
                throw new BizException(ErrorCode.FORBIDDEN, 403, "根目录不允许移动");
            }
            if (targetParent.getId().equals(entity.getId())) {
                throw new BizException(ErrorCode.INVALID_PARAM, 400, "目标目录不能是资源自身");
            }
            ensureNotMoveToDescendant(entity, targetParent);

            String finalName = resolveUniqueName(
                    targetParent.getId(),
                    entity.getName(),
                    entity.getId(),
                    "同名资源过多，请更换名称");
            String oldPath = normalizePath(entity.getPathCache());
            String newPath = buildPath(targetParent.getPathCache(), finalName);

            entity.setParentId(targetParent.getId());
            entity.setName(finalName);
            entity.setNameNormalized(finalName.toLowerCase(Locale.ROOT));
            entity.setPathCache(newPath);
            resourceRepository.updateParentAndNameAndPath(entity);

            if ("folder".equals(entity.getResourceType())) {
                updateDescendantPathCache(entity.getOwnerUserId(), oldPath, newPath);
            }

            Map<String, Object> moved = new LinkedHashMap<String, Object>();
            moved.put("id", entity.getResourceUuid());
            moved.put("parentId", targetParent.getResourceUuid());
            moved.put("name", entity.getName());
            moved.put("type", entity.getResourceType());
            items.add(moved);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("moved", items.size());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> copyResources(String userUuid, CopyResourcesRequestDTO request) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "请求体不合法");
        }
        Resource targetParent = resolveParentFolder(normalizedUserUuid, request.getTargetParentId());
        Set<String> resourceUuids = normalizeResourceIds(request.getResourceIds());
        if (resourceUuids.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "resourceIds不能为空");
        }

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (String resourceUuid : resourceUuids) {
            Resource source = requireActiveResource(normalizedUserUuid, resourceUuid);
            if (source.getParentId() == null) {
                throw new BizException(ErrorCode.FORBIDDEN, 403, "根目录不允许复制");
            }
            ensureNotMoveToDescendant(source, targetParent);
            Resource copied = copyResourceRecursive(source, targetParent);

            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("id", copied.getResourceUuid());
            out.put("parentId", targetParent.getResourceUuid());
            out.put("name", copied.getName());
            out.put("type", copied.getResourceType());
            items.add(out);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("copied", items.size());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> renameResource(String userUuid, String resourceUuid, RenameResourceRequestDTO request) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "请求体不合法");
        }
        Resource entity = requireActiveResource(normalizedUserUuid, resourceUuid);
        if (entity.getParentId() == null) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "根目录不允许重命名");
        }
        Resource parent = resourceRepository.findById(entity.getParentId());
        if (parent == null || parent.getIsDeleted() != null && parent.getIsDeleted().intValue() != 0) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "父目录不存在");
        }

        String oldPath = normalizePath(entity.getPathCache());
        String renamed = resolveUniqueName(entity.getParentId(), request.getName(), entity.getId(), "同名资源过多，请更换名称");
        String newPath = buildPath(parent.getPathCache(), renamed);

        entity.setName(renamed);
        entity.setNameNormalized(renamed.toLowerCase(Locale.ROOT));
        entity.setPathCache(newPath);
        resourceRepository.updateNameAndPath(entity);

        if ("folder".equals(entity.getResourceType())) {
            updateDescendantPathCache(entity.getOwnerUserId(), oldPath, newPath);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", entity.getResourceUuid());
        data.put("parentId", parent.getResourceUuid());
        data.put("name", entity.getName());
        data.put("type", entity.getResourceType());
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> deleteResource(String userUuid, String resourceUuid) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        User user = requireUser(normalizedUserUuid);
        Resource entity = requireActiveResource(normalizedUserUuid, resourceUuid);
        if (entity.getParentId() == null) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "根目录不允许删除");
        }
        RecycleEntry existing = resourceRepository.findActiveRecycleEntryByResourceId(entity.getId());
        if (existing != null) {
            throw new BizException(ErrorCode.CONFLICT, 409, "资源已在回收站");
        }

        resourceRepository.softDeleteById(entity.getId());
        if ("folder".equals(entity.getResourceType())) {
            resourceRepository.softDeleteByPathPrefix(entity.getOwnerUserId(), appendSlash(entity.getPathCache()));
        }

        RecycleEntry entry = new RecycleEntry();
        entry.setResourceId(entity.getId());
        entry.setSpaceId(entity.getSpaceId());
        entry.setDeletedByUserId(user.getId());
        entry.setOriginalParentId(entity.getParentId());
        entry.setPurgeAt(LocalDateTime.now().plusDays(30));
        RecycleEntry history = resourceRepository.findAnyRecycleEntryByResourceId(entity.getId());
        if (history == null) {
            resourceRepository.insertRecycleEntry(entry);
        } else {
            resourceRepository.reactivateRecycleEntry(entry);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", entity.getResourceUuid());
        data.put("deleted", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> restoreResource(String userUuid, String resourceUuid) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        Resource entity = requireResourceAnyStatus(normalizedUserUuid, resourceUuid);
        if (entity.getIsDeleted() == null || entity.getIsDeleted().intValue() == 0) {
            throw new BizException(ErrorCode.CONFLICT, 409, "资源未在回收站中");
        }
        RecycleEntry entry = resourceRepository.findActiveRecycleEntryByResourceId(entity.getId());
        if (entry == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "回收站记录不存在");
        }

        Resource targetParent = resolveRestoreParent(normalizedUserUuid, entry);
        String restoredName = resolveUniqueName(targetParent.getId(), entity.getName(), entity.getId(), "同名资源过多，请更换名称");
        String oldPath = normalizePath(entity.getPathCache());
        String newPath = buildPath(targetParent.getPathCache(), restoredName);

        entity.setParentId(targetParent.getId());
        entity.setName(restoredName);
        entity.setNameNormalized(restoredName.toLowerCase(Locale.ROOT));
        entity.setPathCache(newPath);
        resourceRepository.updateParentAndNameAndPathAnyStatus(entity);

        if ("folder".equals(entity.getResourceType())) {
            resourceRepository.updatePathCachePrefixAll(entity.getOwnerUserId(), appendSlash(oldPath), appendSlash(newPath));
        }
        resourceRepository.restoreById(entity.getId());
        if ("folder".equals(entity.getResourceType())) {
            resourceRepository.restoreByPathPrefix(entity.getOwnerUserId(), appendSlash(newPath));
        }
        resourceRepository.markRecycleEntryRestored(entry.getId());

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", entity.getResourceUuid());
        data.put("parentId", targetParent.getResourceUuid());
        data.put("name", entity.getName());
        data.put("type", entity.getResourceType());
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public RecycleBinListResponseVO listRecycleBin(String userUuid, String keyword, Integer page, Integer pageSize) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        int safePage = page == null ? 1 : page.intValue();
        int safePageSize = pageSize == null ? 20 : pageSize.intValue();
        if (safePage < 1 || safePageSize < 1 || safePageSize > 200) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "分页参数不合法");
        }
        int offset = (safePage - 1) * safePageSize;
        String keywordNormalized = normalizeKeyword(keyword);
        Long total = resourceRepository.countRecycleBin(normalizedUserUuid, keywordNormalized);
        List<RecycleBinRow> rows = resourceRepository.listRecycleBin(
                normalizedUserUuid,
                keywordNormalized,
                offset,
                safePageSize);
        List<RecycleBinItemVO> items = new ArrayList<RecycleBinItemVO>();
        if (rows != null) {
            for (RecycleBinRow row : rows) {
                RecycleBinItemVO item = new RecycleBinItemVO();
                item.setId(row.getResourceUuid());
                item.setName(row.getName());
                item.setType(row.getResourceType());
                item.setSize(row.getSizeBytes() == null ? 0L : row.getSizeBytes().longValue());
                item.setExtension(row.getExtension());
                item.setUpdatedAt(row.getUpdatedAt() == null ? null : row.getUpdatedAt().toString());
                item.setDeletedAt(row.getDeletedAt() == null ? null : row.getDeletedAt().toString());
                item.setPurgeAt(row.getPurgeAt() == null ? null : row.getPurgeAt().toString());
                String originalParentUuid = trim(row.getOriginalParentUuid());
                item.setOriginalParentId(originalParentUuid.isEmpty() ? null : originalParentUuid);
                items.add(item);
            }
        }
        RecycleBinListResponseVO out = new RecycleBinListResponseVO();
        out.setTotal(total == null ? 0L : total.longValue());
        out.setItems(items);
        return out;
    }

    @Override
    @Transactional
    public Map<String, Object> purgeRecycleResources(String userUuid, PurgeRecycleRequestDTO request) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        User user = requireUser(normalizedUserUuid);
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "请求体不合法");
        }
        boolean all = Boolean.TRUE.equals(request.getPurgeAll());
        Set<String> idSet = normalizeResourceIds(request.getResourceIds());
        if (!all && idSet.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "请设置 purgeAll 为 true，或传入 resourceIds");
        }
        int totalRows = 0;
        int rootCount = 0;
        if (all) {
            List<String> roots = resourceRepository.listRecycleRootResourceUuids(normalizedUserUuid);
            if (roots == null || roots.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<String, Object>();
                empty.put("purgedRoots", 0);
                empty.put("deletedResourceRows", Long.valueOf(0L));
                return empty;
            }
            for (String rid : roots) {
                totalRows += purgePermanentSubtree(normalizedUserUuid, user, rid);
                rootCount++;
            }
        } else {
            for (String rid : idSet) {
                totalRows += purgePermanentSubtree(normalizedUserUuid, user, rid);
                rootCount++;
            }
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("purgedRoots", Integer.valueOf(rootCount));
        data.put("deletedResourceRows", Long.valueOf((long) totalRows));
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> buildPreviewFileUrl(String userUuid, String resourceUuid, String requestBaseUrl) {
        return buildFileAccessUrl(userUuid, resourceUuid, requestBaseUrl, "preview");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> buildDownloadFileUrl(String userUuid, String resourceUuid, String requestBaseUrl) {
        return buildFileAccessUrl(userUuid, resourceUuid, requestBaseUrl, "download");
    }

    private Map<String, Object> buildFileAccessUrl(String userUuid, String resourceUuid, String requestBaseUrl, String mode) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        requireUser(normalizedUserUuid);
        Resource resource = requireActiveResource(normalizedUserUuid, resourceUuid);
        if (!"file".equals(trim(resource.getResourceType()))) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "仅支持文件");
        }
        if (resource.getObjectId() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "文件内容不存在");
        }
        StorageObject object = storageObjectRepository.findById(resource.getObjectId());
        if (object == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "存储对象不存在");
        }
        if (!"local".equalsIgnoreCase(trim(object.getStorageProvider()))) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "当前仅支持本地存储");
        }
        Path filePath = Paths.get(trim(object.getObjectKey()).replace("\\", "/"));
        if (!Files.isRegularFile(filePath)) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "本地文件不存在");
        }

        long ttl = appProperties.getFileAccessTtlSeconds();
        if (ttl <= 0L) {
            ttl = 3600L;
        }
        long exp = Instant.now().getEpochSecond() + ttl;
        String secret = appProperties.resolveFileAccessSecret();
        String sig = FileAccessSigner.sign(secret, resource.getResourceUuid(), normalizedUserUuid, exp, mode);

        String base = normalizeRequestBaseUrl(requestBaseUrl);
        String url = base + "/api/v1/file-access?"
                + "resourceId=" + urlEncode(resource.getResourceUuid())
                + "&userId=" + urlEncode(normalizedUserUuid)
                + "&exp=" + exp
                + "&mode=" + urlEncode(mode)
                + "&sig=" + urlEncode(sig);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("url", url);
        data.put("expiresAt", Instant.ofEpochSecond(exp).toString());
        data.put("ttlSeconds", ttl);
        data.put("resourceId", resource.getResourceUuid());
        data.put("filename", resource.getName());
        data.put("size", resource.getSizeBytes() == null ? 0L : resource.getSizeBytes().longValue());
        data.put("mimeType", guessMimeType(filePath, object, resource.getName()));
        data.put("mode", mode);
        return data;
    }

    private static String normalizeRequestBaseUrl(String requestBaseUrl) {
        String base = requestBaseUrl == null ? "" : requestBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String guessMimeType(Path filePath, StorageObject object, String filename) {
        String fromDb = object.getMimeType();
        if (fromDb != null && !fromDb.trim().isEmpty()) {
            return fromDb.trim();
        }
        try {
            String probed = Files.probeContentType(filePath);
            if (probed != null && !probed.trim().isEmpty()) {
                return probed.trim();
            }
        } catch (Exception ignored) {
        }
        String byName = URLConnection.guessContentTypeFromName(filename == null ? "" : filename.trim());
        if (byName != null && !byName.trim().isEmpty()) {
            return byName.trim();
        }
        return "application/octet-stream";
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private Resource resolveParentFolder(String userUuid, String parentResourceUuid) {
        String normalizedParentUuid = trim(parentResourceUuid);
        if (normalizedParentUuid.isEmpty()) {
            return userResourceInitService.ensureRootFolder(userUuid);
        }
        Resource folder = resourceRepository.findFolderByResourceUuid(userUuid, normalizedParentUuid);
        if (folder == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "目录不存在");
        }
        return folder;
    }

    private String normalizeKeyword(String keyword) {
        String v = trim(keyword);
        return v.isEmpty() ? "" : v.toLowerCase(Locale.ROOT);
    }

    private String resolveUniqueName(Long parentId, String originName, Long excludeId, String conflictMsg) {
        String original = trim(originName);
        if (original.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "资源名称不能为空");
        }
        if (original.length() > 255) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "资源名称过长");
        }
        String candidate = original;
        int suffix = 1;
        while (existsActiveName(parentId, candidate, excludeId)) {
            candidate = original + "(" + suffix + ")";
            suffix++;
            if (suffix > 10000) {
                throw new BizException(ErrorCode.CONFLICT, 409, conflictMsg);
            }
        }
        return candidate;
    }

    private boolean existsActiveName(Long parentId, String name, Long excludeId) {
        Integer count;
        if (excludeId == null) {
            count = resourceRepository.countActiveByParentAndNameNormalized(
                    parentId,
                    trim(name).toLowerCase(Locale.ROOT));
        } else {
            count = resourceRepository.countActiveByParentAndNameNormalizedExcludeId(
                    parentId,
                    trim(name).toLowerCase(Locale.ROOT),
                    excludeId);
        }
        return count != null && count.intValue() > 0;
    }

    private Resource requireActiveResource(String userUuid, String resourceUuid) {
        String normalizedResourceUuid = trim(resourceUuid);
        if (normalizedResourceUuid.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "resourceId不能为空");
        }
        Resource entity = resourceRepository.findActiveByResourceUuid(userUuid, normalizedResourceUuid);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "资源不存在");
        }
        return entity;
    }

    private Resource requireResourceAnyStatus(String userUuid, String resourceUuid) {
        String normalizedResourceUuid = trim(resourceUuid);
        if (normalizedResourceUuid.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "resourceId不能为空");
        }
        Resource entity = resourceRepository.findByResourceUuid(userUuid, normalizedResourceUuid);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "资源不存在");
        }
        return entity;
    }

    private User requireUser(String userUuid) {
        User user = userMapper.findByUserUuid(userUuid);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        return user;
    }

    private Resource resolveRestoreParent(String userUuid, RecycleEntry entry) {
        if (entry.getOriginalParentId() != null) {
            Resource parent = resourceRepository.findById(entry.getOriginalParentId());
            if (parent != null && (parent.getIsDeleted() == null || parent.getIsDeleted().intValue() == 0)) {
                return parent;
            }
        }
        return userResourceInitService.ensureRootFolder(userUuid);
    }

    /**
     * 物理删除回收站中的一棵子树（根资源须存在未恢复的 recycle_entries 记录）。
     */
    private int purgePermanentSubtree(String userUuid, User user, String resourceUuid) {
        Resource root = requireResourceAnyStatus(userUuid, resourceUuid);
        if (root.getIsDeleted() == null || root.getIsDeleted().intValue() == 0) {
            throw new BizException(ErrorCode.CONFLICT, 409, "资源未在回收站中");
        }
        RecycleEntry entry = resourceRepository.findActiveRecycleEntryByResourceId(root.getId());
        if (entry == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "回收站记录不存在");
        }
        String folderPrefix = "folder".equals(root.getResourceType()) ? appendSlash(root.getPathCache()) : null;
        List<Resource> subtree = resourceRepository.listDeletedSubtreeForPurge(user.getId(), root.getId(), folderPrefix);
        if (subtree == null || subtree.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "待删除资源不存在");
        }

        resourceRepository.deleteRecycleEntryByResourceId(root.getId());

        List<Long> allIds = new ArrayList<Long>();
        for (Resource r : subtree) {
            allIds.add(r.getId());
        }
        if (!allIds.isEmpty()) {
            resourceRepository.deleteResourceAclByResourceIds(allIds);
            resourceRepository.deleteShareResourcesByResourceIds(allIds);
            resourceRepository.nullifyUploadSessionParentByResourceIds(allIds);
        }

        LinkedHashSet<Long> objectIds = new LinkedHashSet<Long>();
        for (Resource r : subtree) {
            if ("file".equals(r.getResourceType()) && r.getObjectId() != null) {
                objectIds.add(r.getObjectId());
            }
        }

        for (Resource r : subtree) {
            int n = resourceRepository.hardDeleteById(r.getId(), user.getId());
            if (n <= 0) {
                throw new BizException(ErrorCode.NOT_FOUND, 404, "删除资源失败");
            }
        }

        for (Long objectId : objectIds) {
            tryDeleteUnusedStorageObject(objectId);
        }
        return subtree.size();
    }

    private void tryDeleteUnusedStorageObject(Long objectId) {
        Long cnt = resourceRepository.countByObjectId(objectId);
        if (cnt != null && cnt.longValue() > 0L) {
            return;
        }
        StorageObject object = storageObjectRepository.findById(objectId);
        if (object == null) {
            return;
        }
        if ("local".equalsIgnoreCase(trim(object.getStorageProvider()))) {
            Path filePath = Paths.get(trim(object.getObjectKey()).replace("\\", "/"));
            try {
                Files.deleteIfExists(filePath);
            } catch (Exception ignored) {
            }
        }
        storageObjectRepository.deleteById(objectId);
    }

    private Set<String> normalizeResourceIds(List<String> resourceIds) {
        Set<String> result = new LinkedHashSet<String>();
        if (resourceIds == null) {
            return result;
        }
        for (String id : resourceIds) {
            String normalized = trim(id);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private void ensureNotMoveToDescendant(Resource source, Resource targetParent) {
        if (!"folder".equals(source.getResourceType())) {
            return;
        }
        String sourcePath = normalizePath(source.getPathCache());
        String targetPath = normalizePath(targetParent.getPathCache());
        if (targetPath.equals(sourcePath) || targetPath.startsWith(sourcePath + "/")) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "目标目录不能是当前目录或其子目录");
        }
    }

    private void updateDescendantPathCache(Long ownerUserId, String oldPath, String newPath) {
        String oldPrefix = appendSlash(oldPath);
        String newPrefix = appendSlash(newPath);
        resourceRepository.updatePathCachePrefix(ownerUserId, oldPrefix, newPrefix);
    }

    private Resource copyResourceRecursive(Resource source, Resource targetParent) {
        String copiedName = resolveUniqueName(targetParent.getId(), source.getName(), null, "同名资源过多，请更换名称");
        Resource copied = new Resource();
        copied.setResourceUuid(UUID.randomUUID().toString());
        copied.setSpaceId(targetParent.getSpaceId());
        copied.setParentId(targetParent.getId());
        copied.setResourceType(source.getResourceType());
        copied.setName(copiedName);
        copied.setNameNormalized(copiedName.toLowerCase(Locale.ROOT));
        copied.setExtension(source.getExtension());
        copied.setSizeBytes(source.getSizeBytes());
        copied.setObjectId(source.getObjectId());
        copied.setPathCache(buildPath(targetParent.getPathCache(), copiedName));
        copied.setOwnerUserId(targetParent.getOwnerUserId());

        if ("folder".equals(source.getResourceType())) {
            copied.setExtension(null);
            copied.setSizeBytes(0L);
            copied.setObjectId(null);
            resourceRepository.insertFolderResource(copied);
            copyFolderChildren(source, copied);
        } else {
            resourceRepository.insertFileResource(copied);
        }
        return copied;
    }

    private void copyFolderChildren(Resource sourceFolder, Resource copiedFolder) {
        List<Resource> children = resourceRepository.listActiveChildrenByParentId(sourceFolder.getOwnerUserId(), sourceFolder.getId());
        for (Resource child : children) {
            copyResourceRecursive(child, copiedFolder);
        }
    }

    private String normalizePath(String path) {
        String normalized = trim(path);
        if (normalized.isEmpty()) {
            return "/";
        }
        if (!normalized.startsWith("/")) {
            return "/" + normalized;
        }
        return normalized;
    }

    private String appendSlash(String value) {
        String v = normalizePath(value);
        return v.endsWith("/") ? v : v + "/";
    }

    private String buildPath(String parentPath, String name) {
        String p = trim(parentPath);
        if (p.isEmpty()) {
            p = "/";
        }
        if (!p.endsWith("/")) {
            p = p + "/";
        }
        return p + trim(name);
    }
}

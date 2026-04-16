package com.netdisk.service.impl;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.mapper.ResourceRepository;
import com.netdisk.mapper.StorageObjectRepository;
import com.netdisk.mapper.UploadPartRepository;
import com.netdisk.mapper.UploadSessionRepository;
import com.netdisk.mapper.UserRepository;
import com.netdisk.pojo.dto.UploadCompleteRequest;
import com.netdisk.pojo.dto.UploadInitRequest;
import com.netdisk.pojo.entity.ResourceEntity;
import com.netdisk.pojo.entity.StorageObjectEntity;
import com.netdisk.pojo.entity.UploadPartEntity;
import com.netdisk.pojo.entity.UploadSessionEntity;
import com.netdisk.pojo.entity.UserEntity;
import com.netdisk.service.UploadService;
import com.netdisk.service.UserResourceInitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 本地存储上传服务实现。
 */
@Service
public class UploadServiceImpl implements UploadService {
    private final UploadSessionRepository uploadSessionRepository;
    private final UploadPartRepository uploadPartRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final UserResourceInitService userResourceInitService;

    private final Path uploadBaseDir = Paths.get("data", "local-storage", "uploads");
    private final Path objectBaseDir = Paths.get("data", "local-storage", "objects");

    public UploadServiceImpl(
            UploadSessionRepository uploadSessionRepository,
            UploadPartRepository uploadPartRepository,
            StorageObjectRepository storageObjectRepository,
            ResourceRepository resourceRepository,
            UserRepository userRepository,
            UserResourceInitService userResourceInitService) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.uploadPartRepository = uploadPartRepository;
        this.storageObjectRepository = storageObjectRepository;
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
        this.userResourceInitService = userResourceInitService;
    }

    @Override
    @Transactional
    public Map<String, Object> init(String userUuid, UploadInitRequest request) {
        UserEntity user = requireUser(userUuid);
        ResourceEntity parent = resolveParentFolder(userUuid, request.getParentId());
        if (request.getSize() == null || request.getSize().longValue() <= 0L
                || request.getPartSize() == null || request.getPartSize().longValue() <= 0L) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "上传参数不合法");
        }
        long totalPartsLong = (request.getSize() + request.getPartSize() - 1) / request.getPartSize();
        if (totalPartsLong > Integer.MAX_VALUE) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "分片数量过大");
        }

        UploadSessionEntity session = new UploadSessionEntity();
        session.setUploadUuid(UUID.randomUUID().toString());
        session.setUserId(user.getId());
        session.setSpaceId(parent.getSpaceId());
        session.setParentResourceId(parent.getId());
        session.setFilename(request.getFilename());
        session.setTotalSize(request.getSize());
        session.setTotalParts((int) totalPartsLong);
        session.setSha256(normalizeSha256(request.getSha256()));
        session.setStatus("initiated");
        session.setExpiresAt(LocalDateTime.now().plusHours(24));
        uploadSessionRepository.insert(session);

        try {
            Files.createDirectories(uploadBaseDir.resolve(session.getUploadUuid()));
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "创建上传目录失败");
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("uploadId", session.getUploadUuid());
        data.put("partSize", request.getPartSize());
        data.put("totalParts", session.getTotalParts());
        data.put("expiresAt", session.getExpiresAt().toString());
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> uploadPart(String userUuid, String uploadId, Integer partNumber, InputStream stream) {
        UserEntity user = requireUser(userUuid);
        UploadSessionEntity session = requireSession(uploadId, user.getId());
        assertWritable(session);
        if (partNumber == null || partNumber.intValue() < 1 || partNumber.intValue() > session.getTotalParts().intValue()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "分片序号不合法");
        }

        Path partPath = partPath(session.getUploadUuid(), partNumber.intValue());
        long size;
        String etag;
        try {
            Files.createDirectories(partPath.getParent());
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            size = writeStreamAndDigest(stream, partPath, md5);
            etag = hex(md5.digest());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "写入分片失败");
        }

        UploadPartEntity part = new UploadPartEntity();
        part.setUploadSessionId(session.getId());
        part.setPartNumber(partNumber);
        part.setPartSize(size);
        part.setEtag(etag);
        part.setChecksum(null);
        part.setStatus("uploaded");
        uploadPartRepository.upsert(part);
        uploadSessionRepository.updateStatus(session.getId(), "uploading");

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("partNumber", partNumber);
        data.put("etag", etag);
        data.put("size", size);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> complete(String userUuid, String uploadId, UploadCompleteRequest request) {
        UserEntity user = requireUser(userUuid);
        UploadSessionEntity session = requireSession(uploadId, user.getId());
        assertWritable(session);
        List<UploadPartEntity> parts = uploadPartRepository.listByUploadSessionId(session.getId());
        if (parts.size() != session.getTotalParts().intValue()) {
            throw new BizException(ErrorCode.CONFLICT, 409, "分片未上传完成");
        }
        validateSequential(parts, session.getTotalParts().intValue());
        validatePartEtags(request, session.getId());

        Path mergedPath = uploadBaseDir.resolve(session.getUploadUuid()).resolve("merged.bin");
        try {
            mergeParts(session.getUploadUuid(), session.getTotalParts().intValue(), mergedPath);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "合并分片失败");
        }

        String sha256;
        long size;
        try {
            size = Files.size(mergedPath);
            sha256 = digestFile(mergedPath, "SHA-256");
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "校验文件失败");
        }
        if (session.getTotalSize() != null && size != session.getTotalSize().longValue()) {
            throw new BizException(ErrorCode.CONFLICT, 409, "文件大小校验失败");
        }
        if (!trim(session.getSha256()).isEmpty() && !session.getSha256().equalsIgnoreCase(sha256)) {
            throw new BizException(ErrorCode.CONFLICT, 409, "文件摘要校验失败");
        }

        StorageObjectEntity object = storageObjectRepository.findBySha256AndSize(sha256, size);
        if (object == null) {
            Path objectPath = objectPathForSha256(sha256);
            try {
                Files.createDirectories(objectPath.getParent());
                if (!Files.exists(objectPath)) {
                    Files.move(mergedPath, objectPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                throw new BizException(ErrorCode.INTERNAL, 500, "保存对象文件失败");
            }

            object = new StorageObjectEntity();
            object.setObjectUuid(UUID.randomUUID().toString());
            object.setSha256(sha256);
            object.setMd5(null);
            object.setSizeBytes(size);
            object.setMimeType(null);
            object.setStorageProvider("local");
            object.setBucketName("local");
            object.setObjectKey(objectPath.toString().replace("\\", "/"));
            object.setStorageClass("standard");
            storageObjectRepository.insert(object);
        }

        ResourceEntity parent = resourceRepository.findById(session.getParentResourceId());
        if (parent == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "父目录不存在");
        }
        String uniqueFilename = resolveUniqueFilename(parent.getId(), session.getFilename());
        ResourceEntity file = new ResourceEntity();
        file.setResourceUuid(UUID.randomUUID().toString());
        file.setSpaceId(session.getSpaceId());
        file.setParentId(parent.getId());
        file.setName(uniqueFilename);
        file.setNameNormalized(trim(uniqueFilename).toLowerCase(Locale.ROOT));
        file.setExtension(extractExtension(uniqueFilename));
        file.setSizeBytes(size);
        file.setObjectId(object.getId());
        file.setPathCache(buildPath(parent.getPathCache(), uniqueFilename));
        file.setOwnerUserId(session.getUserId());
        resourceRepository.insertFileResource(file);

        uploadSessionRepository.complete(session.getId(), "completed");
        cleanupUploadTemp(session.getUploadUuid(), false);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("resourceId", file.getResourceUuid());
        data.put("filename", uniqueFilename);
        data.put("size", size);
        data.put("sha256", sha256);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> status(String userUuid, String uploadId) {
        UserEntity user = requireUser(userUuid);
        UploadSessionEntity session = requireSession(uploadId, user.getId());
        Integer uploadedParts = uploadPartRepository.countByUploadSessionId(session.getId());
        Long uploadedBytes = uploadPartRepository.sumPartSizeByUploadSessionId(session.getId());

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("uploadId", session.getUploadUuid());
        data.put("status", session.getStatus());
        data.put("filename", session.getFilename());
        data.put("totalSize", session.getTotalSize());
        data.put("uploadedBytes", uploadedBytes == null ? 0L : uploadedBytes.longValue());
        data.put("totalParts", session.getTotalParts());
        data.put("uploadedParts", uploadedParts == null ? 0 : uploadedParts.intValue());
        data.put("expiresAt", session.getExpiresAt() == null ? null : session.getExpiresAt().toString());
        data.put("completedAt", session.getCompletedAt() == null ? null : session.getCompletedAt().toString());
        return data;
    }

    @Override
    @Transactional
    public void cancel(String userUuid, String uploadId) {
        UserEntity user = requireUser(userUuid);
        UploadSessionEntity session = requireSession(uploadId, user.getId());
        if (!"completed".equals(session.getStatus())) {
            uploadSessionRepository.updateStatus(session.getId(), "cancelled");
        }
        uploadPartRepository.deleteByUploadSessionId(session.getId());
        cleanupUploadTemp(session.getUploadUuid(), true);
    }

    private UserEntity requireUser(String userUuid) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        UserEntity user = userRepository.findByUserUuid(normalizedUserUuid);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        return user;
    }

    private ResourceEntity resolveParentFolder(String userUuid, String parentId) {
        if (trim(parentId).isEmpty()) {
            return userResourceInitService.ensureRootFolder(userUuid);
        }
        ResourceEntity folder = resourceRepository.findFolderByResourceUuid(userUuid, trim(parentId));
        if (folder == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "目录不存在");
        }
        return folder;
    }

    private UploadSessionEntity requireSession(String uploadId, Long userId) {
        UploadSessionEntity session = uploadSessionRepository.findByUploadUuid(trim(uploadId));
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "上传会话不存在");
        }
        if (!userId.equals(session.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "无权操作该上传会话");
        }
        return session;
    }

    private void assertWritable(UploadSessionEntity session) {
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.CONFLICT, 409, "上传会话已过期");
        }
        if ("completed".equals(session.getStatus()) || "cancelled".equals(session.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, 409, "上传会话不可写");
        }
    }

    private String normalizeSha256(String value) {
        String normalized = trim(value).toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private Path partPath(String uploadId, int partNumber) {
        return uploadBaseDir.resolve(uploadId).resolve(String.valueOf(partNumber));
    }

    private long writeStreamAndDigest(InputStream stream, Path targetPath, MessageDigest digest) throws IOException {
        if (stream == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "分片内容为空");
        }
        long total = 0L;
        byte[] buffer = new byte[8192];
        try (InputStream in = stream; OutputStream out = Files.newOutputStream(targetPath)) {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) {
                    continue;
                }
                out.write(buffer, 0, n);
                digest.update(buffer, 0, n);
                total += n;
            }
        }
        if (total <= 0L) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "分片内容为空");
        }
        return total;
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void validateSequential(List<UploadPartEntity> parts, int expectedParts) {
        if (parts.size() != expectedParts) {
            throw new BizException(ErrorCode.CONFLICT, 409, "分片未上传完整");
        }
        for (int i = 0; i < parts.size(); i++) {
            int expect = i + 1;
            if (parts.get(i).getPartNumber() == null || parts.get(i).getPartNumber().intValue() != expect) {
                throw new BizException(ErrorCode.CONFLICT, 409, "分片序号不连续");
            }
        }
    }

    private void validatePartEtags(UploadCompleteRequest request, Long uploadSessionId) {
        if (request == null || request.getParts() == null || request.getParts().isEmpty()) {
            return;
        }
        for (UploadCompleteRequest.PartItem p : request.getParts()) {
            if (p == null || p.getPartNumber() == null) {
                continue;
            }
            UploadPartEntity saved = uploadPartRepository.findBySessionIdAndPartNumber(uploadSessionId, p.getPartNumber());
            if (saved == null) {
                throw new BizException(ErrorCode.CONFLICT, 409, "分片不存在");
            }
            String expected = trim(p.getEtag());
            if (!expected.isEmpty() && !expected.equalsIgnoreCase(trim(saved.getEtag()))) {
                throw new BizException(ErrorCode.CONFLICT, 409, "分片ETag不匹配");
            }
        }
    }

    private void mergeParts(String uploadId, int totalParts, Path mergedPath) throws IOException {
        Files.createDirectories(mergedPath.getParent());
        try (OutputStream out = Files.newOutputStream(mergedPath)) {
            byte[] buffer = new byte[8192];
            for (int i = 1; i <= totalParts; i++) {
                Path part = partPath(uploadId, i);
                if (!Files.exists(part)) {
                    throw new BizException(ErrorCode.CONFLICT, 409, "分片文件缺失");
                }
                try (InputStream in = Files.newInputStream(part)) {
                    int n;
                    while ((n = in.read(buffer)) >= 0) {
                        if (n > 0) {
                            out.write(buffer, 0, n);
                        }
                    }
                }
            }
        }
    }

    private String digestFile(Path path, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(path)) {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n > 0) {
                    digest.update(buffer, 0, n);
                }
            }
        }
        return hex(digest.digest());
    }

    private Path objectPathForSha256(String sha256) {
        String prefix = sha256.length() >= 2 ? sha256.substring(0, 2) : "00";
        return objectBaseDir.resolve(prefix).resolve(sha256);
    }

    private String extractExtension(String filename) {
        String name = trim(filename);
        int idx = name.lastIndexOf('.');
        if (idx <= 0 || idx >= name.length() - 1) {
            return null;
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
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

    private String resolveUniqueFilename(Long parentId, String originFilename) {
        String original = trim(originFilename);
        if (original.isEmpty()) {
            original = "unnamed";
        }
        String candidate = original;
        int suffix = 1;
        while (existsActiveName(parentId, candidate)) {
            candidate = appendSuffix(original, suffix);
            suffix++;
            if (suffix > 10000) {
                throw new BizException(ErrorCode.CONFLICT, 409, "同名文件过多，请更换文件名");
            }
        }
        return candidate;
    }

    private boolean existsActiveName(Long parentId, String filename) {
        Integer count = resourceRepository.countActiveByParentAndNameNormalized(
                parentId,
                trim(filename).toLowerCase(Locale.ROOT));
        return count != null && count.intValue() > 0;
    }

    private String appendSuffix(String filename, int suffix) {
        int dot = filename.lastIndexOf('.');
        if (dot > 0 && dot < filename.length() - 1) {
            String base = filename.substring(0, dot);
            String ext = filename.substring(dot);
            return base + "(" + suffix + ")" + ext;
        }
        return filename + "(" + suffix + ")";
    }

    private void cleanupUploadTemp(String uploadId, boolean includeMerged) {
        Path dir = uploadBaseDir.resolve(uploadId);
        if (!Files.exists(dir)) {
            return;
        }
        try {
            List<Path> paths = new ArrayList<Path>();
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.forEach(paths::add);
            }
            for (int i = paths.size() - 1; i >= 0; i--) {
                Path p = paths.get(i);
                if (!includeMerged && p.getFileName() != null && "merged.bin".equals(p.getFileName().toString())) {
                    continue;
                }
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
        }
    }
}

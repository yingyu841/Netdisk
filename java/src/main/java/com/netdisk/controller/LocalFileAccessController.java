package com.netdisk.controller;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.config.AppProperties;
import com.netdisk.mapper.ResourceMapper;
import com.netdisk.mapper.StorageObjectMapper;
import com.netdisk.pojo.entity.Resource;
import com.netdisk.pojo.entity.StorageObject;
import com.netdisk.security.FileAccessSigner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Locale;

/**
 * 本地存储文件流式访问（通过签名 URL，无需 Authorization 头，便于浏览器预览）。
 */
@RestController
@RequestMapping("/api/v1")
public class LocalFileAccessController {
    private final ResourceMapper resourceMapper;
    private final StorageObjectMapper storageObjectMapper;
    private final AppProperties appProperties;

    public LocalFileAccessController(
            ResourceMapper resourceMapper,
            StorageObjectMapper storageObjectMapper,
            AppProperties appProperties) {
        this.resourceMapper = resourceMapper;
        this.storageObjectMapper = storageObjectMapper;
        this.appProperties = appProperties;
    }

    /**
     * 使用预览/下载接口返回的临时 URL 访问文件内容。
     */
    @GetMapping("/file-access")
    public ResponseEntity<org.springframework.core.io.Resource> stream(
            @RequestParam("resourceId") String resourceId,
            @RequestParam("userId") String userId,
            @RequestParam("exp") long exp,
            @RequestParam("mode") String mode,
            @RequestParam("sig") String sig) {
        String normalizedResourceId = trim(resourceId);
        String normalizedUserId = trim(userId);
        String normalizedMode = trim(mode).toLowerCase(Locale.ROOT);
        if (normalizedResourceId.isEmpty() || normalizedUserId.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "参数不合法");
        }
        if (!"preview".equals(normalizedMode) && !"download".equals(normalizedMode)) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "mode不合法");
        }
        long now = Instant.now().getEpochSecond();
        if (exp <= now) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "链接已过期");
        }

        String secret = appProperties.resolveFileAccessSecret();
        if (!FileAccessSigner.verify(secret, normalizedResourceId, normalizedUserId, exp, normalizedMode, sig)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "签名无效");
        }

        Resource entity = resourceMapper.findActiveByResourceUuid(normalizedUserId, normalizedResourceId);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "资源不存在");
        }
        if (!"file".equals(trim(entity.getResourceType()))) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "仅支持文件");
        }
        if (entity.getObjectId() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "文件内容不存在");
        }
        StorageObject object = storageObjectMapper.findById(entity.getObjectId());
        if (object == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "存储对象不存在");
        }
        if (!"local".equalsIgnoreCase(trim(object.getStorageProvider()))) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "当前仅支持本地存储");
        }
        Path path = Paths.get(trim(object.getObjectKey()).replace("\\", "/"));
        if (!Files.isRegularFile(path)) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "本地文件不存在");
        }

        String mime = guessMime(path, object, entity.getName());
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(mime);
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition disposition;
        if ("download".equals(normalizedMode)) {
            disposition = ContentDisposition.builder("attachment")
                    .filename(trim(entity.getName()), StandardCharsets.UTF_8)
                    .build();
        } else {
            disposition = ContentDisposition.builder("inline")
                    .filename(trim(entity.getName()), StandardCharsets.UTF_8)
                    .build();
        }

        long size;
        try {
            size = Files.size(path);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "读取文件失败");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(disposition);
        headers.setContentLength(size);

        FileSystemResource body = new FileSystemResource(path.toFile());
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private static String guessMime(Path filePath, StorageObject object, String filename) {
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
        String byName = URLConnection.guessContentTypeFromName(trim(filename));
        if (byName != null && !byName.trim().isEmpty()) {
            return byName.trim();
        }
        return "application/octet-stream";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

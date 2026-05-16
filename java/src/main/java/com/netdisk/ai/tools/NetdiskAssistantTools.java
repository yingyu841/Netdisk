package com.netdisk.ai.tools;

import com.netdisk.ai.util.FileGeneratorUtil;
import com.netdisk.ai.util.FileParserUtil;
import com.netdisk.mapper.ResourceMapper;
import com.netdisk.mapper.StorageObjectMapper;
import com.netdisk.mapper.UserMapper;
import com.netdisk.pojo.dto.CopyResourcesRequestDTO;
import com.netdisk.pojo.dto.MoveResourcesRequestDTO;
import com.netdisk.pojo.dto.RenameResourceRequestDTO;
import com.netdisk.pojo.entity.Resource;
import com.netdisk.pojo.entity.StorageObject;
import com.netdisk.pojo.entity.User;
import com.netdisk.pojo.vo.ResourceItemVO;
import com.netdisk.pojo.vo.ResourceListResponseVO;
import com.netdisk.service.ResourceService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NetdiskAssistantTools {
    private final ResourceService resourceService;
    private final ResourceMapper resourceMapper;
    private final StorageObjectMapper storageObjectMapper;
    private final UserMapper userMapper;
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    private static final long MAX_READ_SIZE = 100 * 1024L; // 限制读取100KB
    private static final long MAX_CREATE_SIZE = 500 * 1024L; // 创建文件限制500KB
    private static final Path OBJECT_BASE_DIR = Paths.get("data", "local-storage", "objects");

    public NetdiskAssistantTools(ResourceService resourceService, ResourceMapper resourceMapper,
            StorageObjectMapper storageObjectMapper, UserMapper userMapper) {
        this.resourceService = resourceService;
        this.resourceMapper = resourceMapper;
        this.storageObjectMapper = storageObjectMapper;
        this.userMapper = userMapper;
    }

    public static void setCurrentUser(String userUuid) {
        CURRENT_USER.set(userUuid);
    }

    public static void clearCurrentUser() {
        CURRENT_USER.remove();
    }

    private String userUuid() {
        return CURRENT_USER.get();
    }

    @Tool("搜索用户网盘中的文件或文件夹，返回匹配的资源列表。keyword 支持名称模糊匹配和通配符 * 匹配。")
    public String searchFiles(@P("搜索关键词") String keyword) {
        log.info("【工具调用】searchFiles 被调用，keyword={}", keyword);
        try {
            // 将 glob 通配符 * 转换为 SQL LIKE 的 %
            String likePattern = keyword.replace("*", "%");
            ResourceListResponseVO result = resourceService.listResources(userUuid(), null, likePattern, 1, 20);
            if (result == null || result.getItems() == null || result.getItems().isEmpty()) {
                return "未找到与 \"" + keyword + "\" 匹配的文件或文件夹。";
            }
            List<String> lines = result.getItems().stream()
                    .map(r -> String.format("- [%s] %s (%s) UUID:%s", r.getType(), r.getName(), formatSize(r.getSize()),
                            r.getId()))
                    .collect(Collectors.toList());
            return "找到以下资源（最多20条）：\n" + String.join("\n", lines);
        } catch (Exception e) {
            return "搜索出错：" + e.getMessage();
        }
    }

    @Tool("列出指定文件夹下的文件和子文件夹。folderUuid 空字符串表示根目录。")
    public String listFilesInFolder(@P("文件夹资源UUID") String folderUuid) {
        try {
            if (folderUuid == null || folderUuid.trim().isEmpty())
                folderUuid = null;
            ResourceListResponseVO result = resourceService.listResources(userUuid(), folderUuid, null, 1, 50);
            if (result == null || result.getItems() == null || result.getItems().isEmpty())
                return "该文件夹为空。";
            List<String> lines = result.getItems().stream()
                    .map(r -> String.format("- [%s] %s (%s) UUID:%s", r.getType(), r.getName(), formatSize(r.getSize()),
                            r.getId()))
                    .collect(Collectors.toList());
            return (folderUuid == null ? "根目录" : "指定文件夹") + " 下的内容（最多50条）：\n" + String.join("\n", lines);
        } catch (Exception e) {
            return "列出文件夹内容时出错：" + e.getMessage();
        }
    }

    @Tool("获取用户网盘的存储空间使用情况摘要。")
    public String getStorageSummary() {
        try {
            ResourceListResponseVO all = resourceService.listResources(userUuid(), null, null, 1, 10000);
            long totalSize = 0L;
            int fileCount = 0;
            int folderCount = 0;
            if (all != null && all.getItems() != null) {
                for (ResourceItemVO r : all.getItems()) {
                    if ("file".equals(r.getType())) {
                        fileCount++;
                        totalSize += r.getSize() == null ? 0L : r.getSize();
                    } else if ("folder".equals(r.getType()))
                        folderCount++;
                }
            }
            return String.format("存储摘要：\n- 文件数量：%d\n- 文件夹数量：%d\n- 总占用空间：%s", fileCount, folderCount,
                    formatSize(totalSize));
        } catch (Exception e) {
            return "获取存储摘要时出错：" + e.getMessage();
        }
    }

    @Tool("获取用户网盘中最近上传或修改的文件列表。")
    public String getRecentFiles() {
        try {
            ResourceListResponseVO result = resourceService.listResources(userUuid(), null, null, 1, 10);
            if (result == null || result.getItems() == null || result.getItems().isEmpty())
                return "网盘中暂无文件。";
            List<String> lines = result.getItems().stream()
                    .filter(r -> "file".equals(r.getType())).limit(10)
                    .map(r -> String.format("- %s (%s, %s) UUID:%s", r.getName(), formatSize(r.getSize()),
                            r.getUpdatedAt() != null ? r.getUpdatedAt() : "未知时间", r.getId()))
                    .collect(Collectors.toList());
            return "最近文件：\n" + String.join("\n", lines);
        } catch (Exception e) {
            return "获取最近文件时出错：" + e.getMessage();
        }
    }

    @Tool("根据资源UUID获取文件或文件夹的详细信息。")
    public String getFileDetail(@P("资源UUID") String resourceUuid) {
        try {
            ResourceListResponseVO result = resourceService.listResources(userUuid(), null, null, 1, 1000);
            if (result == null || result.getItems() == null)
                return "未找到该资源。";
            return result.getItems().stream()
                    .filter(r -> resourceUuid.equals(r.getId())).findFirst()
                    .map(r -> String.format("资源详情：\n- 名称：%s\n- 类型：%s\n- 大小：%s\n- 更新时间：%s", r.getName(), r.getType(),
                            formatSize(r.getSize()), r.getUpdatedAt() != null ? r.getUpdatedAt() : "未知"))
                    .orElse("未找到UUID为 " + resourceUuid + " 的资源。");
        } catch (Exception e) {
            return "获取资源详情时出错：" + e.getMessage();
        }
    }

    @Tool("移动文件或文件夹到指定的目标文件夹。sourceUuid 源资源UUID，targetFolderUuid 目标文件夹UUID（空字符串或null表示根目录）。")
    public String moveFile(@P("源资源UUID") String sourceUuid, @P("目标文件夹UUID（空字符串表示根目录）") String targetFolderUuid) {
        try {
            MoveResourcesRequestDTO request = new MoveResourcesRequestDTO();
            request.setResourceIds(Collections.singletonList(sourceUuid));
            request.setTargetParentId(
                    targetFolderUuid == null || targetFolderUuid.trim().isEmpty() ? null : targetFolderUuid);
            Map<String, Object> result = resourceService.moveResources(userUuid(), request);
            Integer moved = (Integer) result.get("moved");
            if (moved != null && moved > 0) {
                return "移动成功，已移动 " + moved + " 个资源。";
            }
            return "移动完成：" + result;
        } catch (Exception e) {
            return "移动失败：" + e.getMessage();
        }
    }

    @Tool("复制文件或文件夹到指定的目标文件夹。sourceUuid 源资源UUID，targetFolderUuid 目标文件夹UUID（空字符串或null表示根目录）。")
    public String copyFile(@P("源资源UUID") String sourceUuid, @P("目标文件夹UUID（空字符串表示根目录）") String targetFolderUuid) {
        try {
            CopyResourcesRequestDTO request = new CopyResourcesRequestDTO();
            request.setResourceIds(Collections.singletonList(sourceUuid));
            request.setTargetParentId(
                    targetFolderUuid == null || targetFolderUuid.trim().isEmpty() ? null : targetFolderUuid);
            Map<String, Object> result = resourceService.copyResources(userUuid(), request);
            Integer copied = (Integer) result.get("copied");
            if (copied != null && copied > 0) {
                return "复制成功，已复制 " + copied + " 个资源。";
            }
            return "复制完成：" + result;
        } catch (Exception e) {
            return "复制失败：" + e.getMessage();
        }
    }

    @Tool("重命名文件或文件夹。resourceUuid 要重命名的资源UUID，newName 新的名称。")
    public String renameFile(@P("资源UUID") String resourceUuid, @P("新的名称") String newName) {
        try {
            RenameResourceRequestDTO request = new RenameResourceRequestDTO();
            request.setName(newName);
            Map<String, Object> result = resourceService.renameResource(userUuid(), resourceUuid, request);
            return "重命名成功：" + result.get("name");
        } catch (Exception e) {
            return "重命名失败：" + e.getMessage();
        }
    }

    @Tool("删除文件或文件夹到回收站。resourceUuid 要删除的资源UUID。")
    public String deleteFile(@P("资源UUID") String resourceUuid) {
        try {
            Map<String, Object> result = resourceService.deleteResource(userUuid(), resourceUuid);
            return "删除成功，资源已移到回收站。";
        } catch (Exception e) {
            return "删除失败：" + e.getMessage();
        }
    }

    @Tool("读取文本文件的内容（支持 txt、md、json、xml、java、py 等文本格式）。文件夹和二进制文件不支持读取。resourceUuid 文件资源UUID。")
    public String readFileContent(@P("文件资源UUID") String resourceUuid) {
        try {
            Resource resource = resourceMapper.findActiveByResourceUuid(userUuid(), resourceUuid);
            if (resource == null) {
                return "未找到该资源或资源已删除。";
            }
            if (!"file".equals(resource.getResourceType())) {
                return "该资源不是文件，文件夹无法读取内容。";
            }
            if (resource.getObjectId() == null) {
                return "该文件没有存储内容。";
            }
            StorageObject object = storageObjectMapper.findById(resource.getObjectId());
            if (object == null || object.getObjectKey() == null) {
                return "文件存储对象不存在。";
            }
            Path filePath = Paths.get(object.getObjectKey().replace("\\", "/"));
            if (!Files.isRegularFile(filePath)) {
                return "文件不存在。";
            }
            long size = resource.getSizeBytes() != null ? resource.getSizeBytes() : 0L;
            if (size > MAX_READ_SIZE) {
                return "文件过大（" + formatSize(size) + "），最大支持读取 " + formatSize(MAX_READ_SIZE) + " 的文本文件。";
            }
            String extension = resource.getExtension() != null ? resource.getExtension().toLowerCase() : "";
            if (isBinaryExtension(extension)) {
                return "二进制文件（." + extension + "）不支持读取文本内容。";
            }
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            if (content.length() > MAX_READ_SIZE) {
                return "文件内容过长（" + content.length() + " 字符），截取前 " + MAX_READ_SIZE + " 字符：\n\n"
                        + content.substring(0, (int) MAX_READ_SIZE);
            }
            return "文件内容（" + resource.getName() + "，" + formatSize(size) + "）：\n\n" + content;
        } catch (Exception e) {
            log.error("readFileContent failed, resourceUuid={}, userUuid={}", resourceUuid, userUuid(), e);
            return "读取文件失败：" + e.getMessage();
        }
    }

    private boolean isBinaryExtension(String ext) {
        Set<String> TEXT_EXTS = new HashSet<>(Arrays.asList(
                "txt", "md", "json", "xml", "yaml", "yml", "java", "py", "js", "ts",
                "html", "css", "scss", "sql", "sh", "bat", "ps1", "properties", "log",
                "csv", "ini", "conf", "toml", "go", "c", "cpp", "h", "hpp", "cs",
                "rb", "php", "swift", "kt", "scala", "r", "lua", "pl", "pm"));
        return !TEXT_EXTS.contains(ext);
    }

    @Tool("读取二进制文件内容（支持 Word、Excel、PDF、图片等格式）。resourceUuid 文件资源UUID。")
    public String readBinaryFile(@P("文件资源UUID") String resourceUuid) {
        try {
            Resource resource = resourceMapper.findActiveByResourceUuid(userUuid(), resourceUuid);
            if (resource == null) {
                return "未找到该资源或资源已删除。";
            }
            if (!"file".equals(resource.getResourceType())) {
                return "该资源不是文件，文件夹无法读取内容。";
            }
            if (resource.getObjectId() == null) {
                return "该文件没有存储内容。";
            }
            StorageObject object = storageObjectMapper.findById(resource.getObjectId());
            if (object == null || object.getObjectKey() == null) {
                return "文件存储对象不存在。";
            }
            Path filePath = Paths.get(object.getObjectKey().replace("\\", "/"));
            if (!Files.isRegularFile(filePath)) {
                return "文件不存在。";
            }

            long size = resource.getSizeBytes() != null ? resource.getSizeBytes() : 0L;
            if (size > 50 * 1024 * 1024) {
                return "文件过大（" + formatSize(size) + "），最大支持解析 50MB 的文件。";
            }

            String extension = resource.getExtension() != null ? resource.getExtension().toLowerCase() : "";
            String parsedContent = FileParserUtil.parseFile(filePath, extension);
            return "【文件: " + resource.getName() + " | 大小: " + formatSize(size) + " | 类型: " + extension.toUpperCase()
                    + "】\n\n" + parsedContent;

        } catch (Exception e) {
            log.error("readBinaryFile failed, resourceUuid={}, userUuid={}", resourceUuid, userUuid(), e);
            return "读取文件失败：" + e.getMessage();
        }
    }

    @Tool("在用户网盘中创建文件（支持 txt、md、json、xml、java、py 等文本文件，以及 docx、xlsx、pdf、jpg、png 等二进制文件）。parentFolderUuid 目标文件夹UUID（空字符串或null表示根目录），filename 文件名（如 report.docx、data.xlsx），content 文件内容。")
    public String generateFile(String parentFolderUuid, @P("文件名") String filename, @P("文件内容") String content) {
        try {
            String pfUuid = "";
            if (parentFolderUuid != null) {
                pfUuid = ("null".equals(parentFolderUuid) || parentFolderUuid.trim().isEmpty()) ? "" : parentFolderUuid;
            }
            User user = userMapper.findByUserUuid(userUuid());
            if (user == null) {
                return "用户不存在。";
            }
            if (filename == null || filename.trim().isEmpty()) {
                return "文件名不能为空。";
            }

            String ext = extractExtension(filename);
            if (ext == null) {
                ext = "txt";
            }
            ext = ext.toLowerCase(Locale.ROOT);

            // 生成文件内容
            byte[] contentBytes;
            try {
                contentBytes = FileGeneratorUtil.generateFile(filename, content);
            } catch (Exception e) {
                return "生成文件内容失败：" + e.getMessage();
            }

            long size = contentBytes.length;
            if (size > 10 * 1024 * 1024) {
                return "生成的文件过大（" + formatSize(size) + "），最大支持 10MB。";
            }

            // 计算 SHA256
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (Exception e) {
                return "计算文件摘要失败：" + e.getMessage();
            }
            String sha256 = bytesToHex(digest.digest(contentBytes));

            // 解析目标文件夹
            Resource parent = resolveParentFolder(userUuid(), pfUuid);

            // 检查同名文件
            String normalizedName = filename.trim().toLowerCase(Locale.ROOT);
            Integer exists = resourceMapper.countActiveByParentAndNameNormalized(parent.getId(), normalizedName);
            if (exists != null && exists > 0) {
                return "该文件夹下已存在同名文件（" + filename + "），请更换文件名或先删除原文件。";
            }

            // 写入文件
            String prefix = sha256.length() >= 2 ? sha256.substring(0, 2) : "00";
            Path objectPath = OBJECT_BASE_DIR.resolve(prefix).resolve(sha256);
            try {
                Files.createDirectories(objectPath.getParent());
                Files.write(objectPath, contentBytes);
            } catch (Exception e) {
                return "写入文件失败：" + e.getMessage();
            }

            // 创建存储对象
            StorageObject object = new StorageObject();
            object.setObjectUuid(UUID.randomUUID().toString());
            object.setSha256(sha256);
            object.setMd5(null);
            object.setSizeBytes(size);
            object.setMimeType(guessMimeType(filename));
            object.setStorageProvider("local");
            object.setBucketName("");
            object.setObjectKey(objectPath.toString().replace("\\", "/"));
            object.setStorageClass("");
            object.setCreatedAt(LocalDateTime.now());
            object.setUpdatedAt(LocalDateTime.now());
            int objInserted = storageObjectMapper.insert(object);
            log.info("generateFile: storage_object inserted, id={}, objInserted={}, objectKey={}", object.getId(),
                    objInserted, object.getObjectKey());

            // 创建资源记录
            Resource resource = new Resource();
            resource.setResourceUuid(UUID.randomUUID().toString());
            resource.setSpaceId(parent.getSpaceId());
            resource.setParentId(parent.getId());
            resource.setResourceType("file");
            resource.setName(filename.trim());
            resource.setNameNormalized(normalizedName);
            resource.setExtension(ext);
            resource.setSizeBytes(size);
            resource.setObjectId(object.getId());
            resource.setPathCache(buildPath(parent.getPathCache(), filename.trim()));
            resource.setOwnerUserId(user.getId());
            resource.setCreatedAt(LocalDateTime.now());
            resource.setUpdatedAt(LocalDateTime.now());
            int resInserted = resourceMapper.insertFileResource(resource);
            log.info(
                    "generateFile: resource inserted, id={}, resInserted={}, name={}, spaceId={}, parentId={}, objectId={}",
                    resource.getId(), resInserted, resource.getName(), resource.getSpaceId(), resource.getParentId(),
                    resource.getObjectId());

            // 刷新父文件夹大小
            resourceService.refreshFolderSize(parent.getId());

            return "文件创建成功：\n- 文件名：" + filename + "\n- 大小：" + formatSize(size) + "\n- 类型：" + ext.toUpperCase()
                    + "\n- 位置：" + (parent.getResourceUuid() == null ? "根目录" : "文件夹内");
        } catch (Exception e) {
            log.error("generateFile failed, filename={}, userUuid={}", filename, userUuid(), e);
            return "创建文件失败：" + e.getMessage();
        }
    }

    @Tool("在用户网盘中创建新的文本文件。parentFolderUuid 目标文件夹UUID（空字符串或null表示根目录），filename 文件名（如 note.txt），content 文件文本内容。注意：无法创建 Word/Excel 等二进制Office文档，只能创建纯文本文件。")
    public String createFile(String parentFolderUuid, @P("文件名") String filename, @P("文件内容") String content) {
        try {
            String pfUuid = "";
            if (parentFolderUuid != null) {
                pfUuid = ("null".equals(parentFolderUuid) || parentFolderUuid.trim().isEmpty()) ? "" : parentFolderUuid;
            }
            User user = userMapper.findByUserUuid(userUuid());
            if (user == null) {
                return "用户不存在。";
            }
            if (filename == null || filename.trim().isEmpty()) {
                return "文件名不能为空。";
            }
            String ext = extractExtension(filename);
            if (ext != null && isBinaryExtension(ext.toLowerCase(Locale.ROOT))) {
                return "不支持创建二进制文件（." + ext + "），仅支持创建文本文件，如 txt、md、json、xml、java、py 等。";
            }
            byte[] contentBytes = content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0];
            long size = contentBytes.length;
            if (size > MAX_CREATE_SIZE) {
                return "文件内容过大（" + formatSize(size) + "），最大支持创建 " + formatSize(MAX_CREATE_SIZE) + " 的文件。";
            }
            // 计算 SHA256
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (Exception e) {
                return "计算文件摘要失败：" + e.getMessage();
            }
            String sha256 = bytesToHex(digest.digest(contentBytes));
            // 解析目标文件夹
            Resource parent = resolveParentFolder(userUuid(), pfUuid);
            // 检查同名文件
            String normalizedName = filename.trim().toLowerCase(Locale.ROOT);
            Integer exists = resourceMapper.countActiveByParentAndNameNormalized(parent.getId(), normalizedName);
            if (exists != null && exists > 0) {
                return "该文件夹下已存在同名文件（" + filename + "），请更换文件名。";
            }
            // 写入文件
            String prefix = sha256.length() >= 2 ? sha256.substring(0, 2) : "00";
            Path objectPath = OBJECT_BASE_DIR.resolve(prefix).resolve(sha256);
            try {
                Files.createDirectories(objectPath.getParent());
                Files.write(objectPath, contentBytes);
            } catch (Exception e) {
                return "写入文件失败：" + e.getMessage();
            }
            // 创建存储对象
            StorageObject object = new StorageObject();
            object.setObjectUuid(UUID.randomUUID().toString());
            object.setSha256(sha256);
            object.setMd5(null); // 可选
            object.setSizeBytes(size);
            object.setMimeType(guessMimeType(filename));
            object.setStorageProvider("local");
            object.setBucketName("");
            object.setObjectKey(objectPath.toString().replace("\\", "/"));
            object.setStorageClass("");
            object.setCreatedAt(LocalDateTime.now());
            object.setUpdatedAt(LocalDateTime.now());
            int objInserted = storageObjectMapper.insert(object);
            log.info("createFile: storage_object inserted, id={}, objInserted={}, objectKey={}", object.getId(),
                    objInserted, object.getObjectKey());
            // 创建资源记录
            Resource resource = new Resource();
            resource.setResourceUuid(UUID.randomUUID().toString());
            resource.setSpaceId(parent.getSpaceId());
            resource.setParentId(parent.getId());
            resource.setResourceType("file");
            resource.setName(filename.trim());
            resource.setNameNormalized(normalizedName);
            resource.setExtension(ext);
            resource.setSizeBytes(size);
            resource.setObjectId(object.getId());
            resource.setPathCache(buildPath(parent.getPathCache(), filename.trim()));
            resource.setOwnerUserId(user.getId());
            resource.setCreatedAt(LocalDateTime.now());
            resource.setUpdatedAt(LocalDateTime.now());
            int resInserted = resourceMapper.insertFileResource(resource);
            log.info(
                    "createFile: resource inserted, id={}, resInserted={}, name={}, spaceId={}, parentId={}, objectId={}",
                    resource.getId(), resInserted, resource.getName(), resource.getSpaceId(), resource.getParentId(),
                    resource.getObjectId());

            // 刷新父文件夹大小
            resourceService.refreshFolderSize(parent.getId());

            return "文件创建成功：\n- 文件名：" + filename + "\n- 大小：" + formatSize(size) + "\n- 位置："
                    + (parent.getResourceUuid() == null ? "根目录" : "文件夹内");
        } catch (Exception e) {
            return "创建文件失败：" + e.getMessage();
        }
    }

    @Tool("在用户网盘中创建新文件夹。parentFolderUuid 目标文件夹UUID（空字符串或null表示根目录），folderName 文件夹名称。")
    public String createFolder(String parentFolderUuid, @P("文件夹名称") String folderName) {
        try {
            String pfUuid = "";
            if (parentFolderUuid != null) {
                pfUuid = ("null".equals(parentFolderUuid) || parentFolderUuid.trim().isEmpty()) ? "" : parentFolderUuid;
            }
            User user = userMapper.findByUserUuid(userUuid());
            if (user == null) {
                return "用户不存在。";
            }
            if (folderName == null || folderName.trim().isEmpty()) {
                return "文件夹名称不能为空。";
            }
            String trimmedName = folderName.trim();
            // 解析目标文件夹
            Resource parent = resolveParentFolder(userUuid(), pfUuid);
            // 检查同名文件夹
            String normalizedName = trimmedName.toLowerCase(Locale.ROOT);
            Integer exists = resourceMapper.countActiveByParentAndNameNormalized(parent.getId(), normalizedName);
            if (exists != null && exists > 0) {
                return "该文件夹下已存在同名文件夹（" + folderName + "），请更换名称。";
            }
            // 创建文件夹资源
            Resource folder = new Resource();
            folder.setResourceUuid(UUID.randomUUID().toString());
            folder.setSpaceId(parent.getSpaceId());
            folder.setParentId(parent.getId());
            folder.setResourceType("folder");
            folder.setName(trimmedName);
            folder.setNameNormalized(normalizedName);
            folder.setExtension(null);
            folder.setSizeBytes(0L);
            folder.setObjectId(null);
            folder.setPathCache(buildPath(parent.getPathCache(), trimmedName));
            folder.setOwnerUserId(user.getId());
            folder.setCreatedAt(LocalDateTime.now());
            folder.setUpdatedAt(LocalDateTime.now());
            int resInserted = resourceMapper.insertFolderResource(folder);
            log.info("createFolder: folder inserted, id={}, resInserted={}, name={}, spaceId={}, parentId={}",
                    folder.getId(), resInserted, folder.getName(), folder.getSpaceId(), folder.getParentId());
            return "文件夹创建成功：\n- 文件夹名：" + trimmedName + "\n- 位置：" + (parent.getResourceUuid() == null ? "根目录" : "文件夹内");
        } catch (Exception e) {
            log.error("createFolder failed, folderName={}, userUuid={}", folderName, userUuid(), e);
            return "创建文件夹失败：" + e.getMessage();
        }
    }

    private Resource resolveParentFolder(String userUuid, String parentFolderUuid) {
        if (parentFolderUuid == null || parentFolderUuid.trim().isEmpty()) {
            return resourceMapper.findRootFolderByUserUuid(userUuid);
        }
        Resource folder = resourceMapper.findFolderByResourceUuid(userUuid, parentFolderUuid.trim());
        if (folder == null) {
            throw new RuntimeException("目标文件夹不存在");
        }
        return folder;
    }

    private String extractExtension(String filename) {
        if (filename == null)
            return null;
        int idx = filename.lastIndexOf('.');
        if (idx <= 0 || idx >= filename.length() - 1) {
            return null;
        }
        return filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private String buildPath(String parentPath, String name) {
        String p = parentPath == null ? "" : parentPath.trim();
        if (p.isEmpty())
            p = "/";
        if (!p.endsWith("/"))
            p = p + "/";
        return p + name.trim();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String guessMimeType(String filename) {
        if (filename == null)
            return "application/octet-stream";
        int idx = filename.lastIndexOf('.');
        String ext = idx >= 0 ? filename.substring(idx + 1).toLowerCase(Locale.ROOT) : "";
        if (ext.equals("txt") || ext.equals("log"))
            return "text/plain";
        if (ext.equals("html") || ext.equals("htm"))
            return "text/html";
        if (ext.equals("css"))
            return "text/css";
        if (ext.equals("js"))
            return "application/javascript";
        if (ext.equals("json"))
            return "application/json";
        if (ext.equals("xml"))
            return "application/xml";
        if (ext.equals("java"))
            return "text/x-java-source";
        if (ext.equals("py"))
            return "text/x-python";
        if (ext.equals("md"))
            return "text/markdown";
        if (ext.equals("yaml") || ext.equals("yml"))
            return "text/yaml";
        if (ext.equals("sql"))
            return "text/x-sql";
        if (ext.equals("sh"))
            return "text/x-sh";
        if (ext.equals("properties") || ext.equals("csv"))
            return "text/plain";
        return "application/octet-stream";
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes < 0)
            return "未知";
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

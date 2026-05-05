package com.netdisk.service.impl;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.mapper.ResourceMapper;
import com.netdisk.mapper.SpaceMapper;
import com.netdisk.mapper.UserMapper;
import com.netdisk.pojo.entity.Resource;
import com.netdisk.pojo.entity.Space;
import com.netdisk.pojo.entity.User;
import com.netdisk.service.UserResourceInitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 用户资源初始化服务实现。
 */
@Service
public class UserResourceInitServiceImpl implements UserResourceInitService {
    private final UserMapper userRepository;
    private final SpaceMapper spaceRepository;
    private final ResourceMapper resourceRepository;

    public UserResourceInitServiceImpl(
            UserMapper userRepository,
            SpaceMapper spaceRepository,
            ResourceMapper resourceRepository) {
        this.userRepository = userRepository;
        this.spaceRepository = spaceRepository;
        this.resourceRepository = resourceRepository;
    }

    @Override
    @Transactional
    public Resource ensureRootFolder(String userUuid) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        Resource root = resourceRepository.findRootFolderByUserUuid(normalizedUserUuid);
        if (root != null) {
            return root;
        }

        User user = userRepository.findByUserUuid(normalizedUserUuid);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }

        Space space = spaceRepository.findPersonalByOwnerUserId(user.getId());
        if (space == null) {
            space = new Space();
            space.setSpaceUuid(UUID.randomUUID().toString());
            space.setOwnerUserId(user.getId());
            space.setName("个人空间");
            space.setSpaceType("personal");
            space.setStatus(1);
            spaceRepository.insert(space);
        }

        Resource rootFolder = new Resource();
        rootFolder.setResourceUuid(UUID.randomUUID().toString());
        rootFolder.setSpaceId(space.getId());
        rootFolder.setName("我的文件");
        rootFolder.setNameNormalized("我的文件");
        rootFolder.setPathCache("/");
        rootFolder.setOwnerUserId(user.getId());
        resourceRepository.insertRootFolder(rootFolder);

        Resource created = resourceRepository.findRootFolderByUserUuid(normalizedUserUuid);
        if (created == null) {
            throw new BizException(ErrorCode.INTERNAL, 500, "根目录初始化失败");
        }
        return created;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

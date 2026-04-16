package com.netdisk.service.impl;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.mapper.ResourceRepository;
import com.netdisk.mapper.SpaceRepository;
import com.netdisk.mapper.UserRepository;
import com.netdisk.pojo.entity.ResourceEntity;
import com.netdisk.pojo.entity.SpaceEntity;
import com.netdisk.pojo.entity.UserEntity;
import com.netdisk.service.UserResourceInitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 用户资源初始化服务实现。
 */
@Service
public class UserResourceInitServiceImpl implements UserResourceInitService {
    private final UserRepository userRepository;
    private final SpaceRepository spaceRepository;
    private final ResourceRepository resourceRepository;

    public UserResourceInitServiceImpl(
            UserRepository userRepository,
            SpaceRepository spaceRepository,
            ResourceRepository resourceRepository) {
        this.userRepository = userRepository;
        this.spaceRepository = spaceRepository;
        this.resourceRepository = resourceRepository;
    }

    @Override
    @Transactional
    public ResourceEntity ensureRootFolder(String userUuid) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        ResourceEntity root = resourceRepository.findRootFolderByUserUuid(normalizedUserUuid);
        if (root != null) {
            return root;
        }

        UserEntity user = userRepository.findByUserUuid(normalizedUserUuid);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }

        SpaceEntity space = spaceRepository.findPersonalByOwnerUserId(user.getId());
        if (space == null) {
            space = new SpaceEntity();
            space.setSpaceUuid(UUID.randomUUID().toString());
            space.setOwnerUserId(user.getId());
            space.setName("个人空间");
            space.setSpaceType("personal");
            space.setStatus(1);
            spaceRepository.insert(space);
        }

        ResourceEntity rootFolder = new ResourceEntity();
        rootFolder.setResourceUuid(UUID.randomUUID().toString());
        rootFolder.setSpaceId(space.getId());
        rootFolder.setName("我的文件");
        rootFolder.setNameNormalized("我的文件");
        rootFolder.setPathCache("/");
        rootFolder.setOwnerUserId(user.getId());
        resourceRepository.insertRootFolder(rootFolder);

        ResourceEntity created = resourceRepository.findRootFolderByUserUuid(normalizedUserUuid);
        if (created == null) {
            throw new BizException(ErrorCode.INTERNAL, 500, "根目录初始化失败");
        }
        return created;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

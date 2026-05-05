package com.netdisk.service.impl;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.mapper.AuditLogMapper;
import com.netdisk.mapper.UserMapper;
import com.netdisk.pojo.entity.AuditLog;
import com.netdisk.pojo.entity.User;
import com.netdisk.pojo.vo.ActivityItemVO;
import com.netdisk.pojo.vo.ActivityListResponseVO;
import com.netdisk.service.ActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 个人活动服务实现类。
 */
@Service
public class ActivityServiceImpl implements ActivityService {
    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;

    public ActivityServiceImpl(UserMapper userMapper, AuditLogMapper auditLogMapper) {
        this.userMapper = userMapper;
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityListResponseVO listActivities(String userUuid, Integer page, Integer pageSize) {
        String normalizedUserUuid = trim(userUuid);
        if (normalizedUserUuid.isEmpty() || "null".equalsIgnoreCase(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }

        int safePage = page == null ? 1 : page.intValue();
        int safePageSize = pageSize == null ? 20 : pageSize.intValue();
        if (safePage < 1 || safePageSize < 1 || safePageSize > 200) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "分页参数不合法");
        }

        User user = userMapper.findByUserUuid(normalizedUserUuid);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }

        int offset = (safePage - 1) * safePageSize;
        Long total = auditLogMapper.countByActorUserId(user.getId());
        List<AuditLog> entities = auditLogMapper.listByActorUserId(user.getId(), offset, safePageSize);

        ActivityListResponseVO out = new ActivityListResponseVO();
        out.setTotal(total == null ? 0L : total.longValue());
        out.setItems(toItems(entities));
        return out;
    }

    private List<ActivityItemVO> toItems(List<AuditLog> entities) {
        List<ActivityItemVO> items = new ArrayList<ActivityItemVO>();
        if (entities == null) {
            return items;
        }
        for (AuditLog entity : entities) {
            ActivityItemVO item = new ActivityItemVO();
            String eventId = trim(entity.getEventUuid());
            if (eventId.isEmpty()) {
                eventId = entity.getId() == null ? "" : String.valueOf(entity.getId());
            }
            item.setEventId(eventId);
            item.setActorType(entity.getActorType());
            item.setEventType(entity.getEventType());
            item.setTargetType(entity.getTargetType());
            item.setTargetId(entity.getTargetId());
            item.setResult(entity.getResult());
            item.setReason(entity.getReason());
            item.setMetadata(entity.getMetadataJson());
            item.setCreatedAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString());
            items.add(item);
        }
        return items;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

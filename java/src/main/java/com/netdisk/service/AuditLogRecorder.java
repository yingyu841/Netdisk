package com.netdisk.service;

import com.netdisk.mapper.AuditLogMapper;
import com.netdisk.pojo.entity.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 审计日志写入（失败不影响主业务）。
 */
@Service
public class AuditLogRecorder {
    private static final Logger log = LoggerFactory.getLogger(AuditLogRecorder.class);
    private final AuditLogMapper auditLogMapper;

    public AuditLogRecorder(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(AuditLog row) {
        if (row == null) {
            return;
        }
        try {
            auditLogMapper.insert(row);
        } catch (Exception ex) {
            log.warn("audit log insert failed eventType={} targetId={}", row.getEventType(), row.getTargetId(), ex);
        }
    }
}

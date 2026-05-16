-- 触发器：当 users.storage_quota_bytes 更新时，同步更新 spaces.quota_bytes（仅限个人空间）
DELIMITER //

CREATE TRIGGER trg_users_quota_sync_after_update
AFTER UPDATE ON users
FOR EACH ROW
BEGIN
    IF OLD.storage_quota_bytes <=> NEW.storage_quota_bytes THEN
        UPDATE spaces
        SET quota_bytes = NEW.storage_quota_bytes,
            updated_at = NOW()
        WHERE owner_user_id = NEW.id
          AND space_type = 'personal'
          AND status = 1;
    END IF;
END//

DELIMITER ;

-- 同时将现有的 spaces 表配额统一改为 1GB（与 users 表一致）
UPDATE spaces SET quota_bytes = 1073741824 WHERE space_type = 'personal';

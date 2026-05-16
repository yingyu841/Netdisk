-- AI 智能体会话表
CREATE TABLE IF NOT EXISTS ai_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_uuid VARCHAR(64) NOT NULL COMMENT '用户UUID',
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话ID（UUID）',
    title VARCHAR(255) DEFAULT '新对话' COMMENT '会话标题',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_uuid (user_uuid),
    INDEX idx_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI智能体会话';

-- AI 智能体消息表（已存在 ai_agent_conversation 表，这里改名更清晰）
-- 注意：ai_agent_conversation 表已存在且有数据，无需重建
-- 以下 SQL 用于补充查询会话列表的功能

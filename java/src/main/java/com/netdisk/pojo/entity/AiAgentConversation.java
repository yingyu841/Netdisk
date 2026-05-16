package com.netdisk.pojo.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class AiAgentConversation {
    private Long id;
    private String userUuid;
    private String conversationId;
    private String role;
    private String content;
    private String toolName;
    private LocalDateTime createdAt;
}

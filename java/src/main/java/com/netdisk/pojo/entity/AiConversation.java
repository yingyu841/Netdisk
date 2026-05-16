package com.netdisk.pojo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiConversation {
    private Long id;
    private String userUuid;
    private String conversationId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

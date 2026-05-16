package com.netdisk.pojo.vo;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class AiConversationVO {
    private String conversationId;
    private LocalDateTime lastMessageAt;
    private Integer messageCount;
    private List<AiMessageVO> messages;
    @Data public static class AiMessageVO {
        private String role;
        private String content;
        private String toolName;
        private LocalDateTime createdAt;
    }
}

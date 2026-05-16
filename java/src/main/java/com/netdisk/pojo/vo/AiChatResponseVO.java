package com.netdisk.pojo.vo;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class AiChatResponseVO {
    private String conversationId;
    private String reply;
    private List<String> toolsUsed;
    private LocalDateTime repliedAt;
}

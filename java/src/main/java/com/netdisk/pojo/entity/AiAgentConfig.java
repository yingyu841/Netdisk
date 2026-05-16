package com.netdisk.pojo.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class AiAgentConfig {
    private Long id;
    private String userUuid;
    private String modelName;
    private Double temperature;
    private Integer maxTokens;
    private String systemPrompt;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.netdisk.pojo.dto;
import lombok.Data;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
@Data
public class AiAgentConfigDTO {
    @Size(max=64) private String modelName;
    @Min(0) @Max(20) private Double temperature;
    @Min(256) @Max(8192) private Integer maxTokens;
    @Size(max=4000) private String systemPrompt;
    private Boolean enabled;
}

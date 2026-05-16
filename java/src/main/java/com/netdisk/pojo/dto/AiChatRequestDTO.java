package com.netdisk.pojo.dto;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
@Data
public class AiChatRequestDTO {
    @NotBlank @Size(max=4000)
    private String message;
    private String conversationId;
    private Boolean useHistory = true;
}

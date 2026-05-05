package com.netdisk.pojo.dto.chat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class EditMessageRequestDTO {
    @NotBlank(message = "contentText不能为空")
    @Size(max = 5000, message = "contentText长度不能超过5000")
    private String contentText;

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }
}

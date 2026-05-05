package com.netdisk.pojo.dto.chat;

import javax.validation.constraints.NotBlank;

public class PinMessageRequestDTO {
    @NotBlank(message = "messageId不能为空")
    private String messageId;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}

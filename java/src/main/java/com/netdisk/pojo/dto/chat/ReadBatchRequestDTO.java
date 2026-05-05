package com.netdisk.pojo.dto.chat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

public class ReadBatchRequestDTO {
    @NotBlank(message = "conversationId不能为空")
    private String conversationId;
    @NotEmpty(message = "messageIds不能为空")
    private List<String> messageIds;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public List<String> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<String> messageIds) {
        this.messageIds = messageIds;
    }
}

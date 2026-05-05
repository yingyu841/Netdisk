package com.netdisk.pojo.dto.chat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Map;

public class SendMessageRequestDTO {
    @NotBlank(message = "messageType必填")
    private String messageType;
    @Size(max = 5000, message = "contentText长度不能超过5000")
    private String contentText;
    private Map<String, Object> content;
    private String replyToMessageId;
    private String resourceId;
    @Size(max = 64, message = "clientMessageId长度不能超过64")
    private String clientMessageId;

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }

    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }
}

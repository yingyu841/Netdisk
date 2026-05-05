package com.netdisk.pojo.dto.chat;

import javax.validation.constraints.Size;

public class UpdateConversationRequestDTO {
    @Size(max = 128, message = "name长度不能超过128")
    private String name;
    @Size(max = 512, message = "avatarUrl长度不能超过512")
    private String avatarUrl;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}

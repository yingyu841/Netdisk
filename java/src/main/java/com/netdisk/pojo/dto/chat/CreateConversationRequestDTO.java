package com.netdisk.pojo.dto.chat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

public class CreateConversationRequestDTO {
    @NotBlank(message = "conversationType必填")
    private String conversationType;
    private String spaceId;
    @Size(max = 128, message = "name长度不能超过128")
    private String name;
    @Size(max = 512, message = "avatarUrl长度不能超过512")
    private String avatarUrl;
    @NotEmpty(message = "memberUserIds不能为空")
    private List<String> memberUserIds;

    public String getConversationType() {
        return conversationType;
    }

    public void setConversationType(String conversationType) {
        this.conversationType = conversationType;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(String spaceId) {
        this.spaceId = spaceId;
    }

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

    public List<String> getMemberUserIds() {
        return memberUserIds;
    }

    public void setMemberUserIds(List<String> memberUserIds) {
        this.memberUserIds = memberUserIds;
    }
}

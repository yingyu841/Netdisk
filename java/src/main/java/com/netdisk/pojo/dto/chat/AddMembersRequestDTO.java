package com.netdisk.pojo.dto.chat;

import javax.validation.constraints.NotEmpty;
import java.util.List;

public class AddMembersRequestDTO {
    @NotEmpty(message = "userIds不能为空")
    private List<String> userIds;

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }
}

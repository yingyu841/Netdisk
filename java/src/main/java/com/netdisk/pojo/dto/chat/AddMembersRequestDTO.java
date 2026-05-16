package com.netdisk.pojo.dto.chat;

import javax.validation.constraints.Size;
import java.util.List;

public class AddMembersRequestDTO {
    @Size(max = 100, message = "userIds数量不能超过100")
    private List<String> userIds;

    @Size(max = 100, message = "emails数量不能超过100")
    private List<String> emails;

    @Size(max = 100, message = "phones数量不能超过100")
    private List<String> phones;

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }

    public List<String> getEmails() {
        return emails;
    }

    public void setEmails(List<String> emails) {
        this.emails = emails;
    }

    public List<String> getPhones() {
        return phones;
    }

    public void setPhones(List<String> phones) {
        this.phones = phones;
    }
}

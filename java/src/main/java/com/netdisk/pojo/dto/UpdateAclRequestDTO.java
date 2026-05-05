package com.netdisk.pojo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 更新资源 ACL 请求。
 */
@Data
public class UpdateAclRequestDTO {
    private Boolean inheritFlag;
    private List<Entry> entries = new ArrayList<Entry>();

    @Data
    public static class Entry {
        private String subjectType;
        private String subjectId;
        private Boolean canRead;
        private Boolean canWrite;
        private Boolean canDelete;
        private Boolean canShare;
        private Boolean canDownload;
        private Boolean canManageAcl;
    }
}

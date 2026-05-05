package com.netdisk.pojo.dto;

import java.util.List;

/**
 * 彻底删除回收站资源请求。
 * <p>
 * purgeAll 为 true 时清空回收站；否则按 resourceIds 批量彻底删除（单条时列表仅含一个 id）。
 */
public class PurgeRecycleRequestDTO {
    private Boolean purgeAll;
    private List<String> resourceIds;

    public Boolean getPurgeAll() {
        return purgeAll;
    }

    public void setPurgeAll(Boolean purgeAll) {
        this.purgeAll = purgeAll;
    }

    public List<String> getResourceIds() {
        return resourceIds;
    }

    public void setResourceIds(List<String> resourceIds) {
        this.resourceIds = resourceIds;
    }
}

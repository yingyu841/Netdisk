package com.netdisk.pojo.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 移动资源请求。
 */
public class MoveResourcesRequestDTO {
    @NotEmpty(message = "resourceIds必填")
    private List<String> resourceIds;

    @NotBlank(message = "targetParentId必填")
    private String targetParentId;

    public List<String> getResourceIds() {
        return resourceIds;
    }

    public void setResourceIds(List<String> resourceIds) {
        this.resourceIds = resourceIds;
    }

    public String getTargetParentId() {
        return targetParentId;
    }

    public void setTargetParentId(String targetParentId) {
        this.targetParentId = targetParentId;
    }
}

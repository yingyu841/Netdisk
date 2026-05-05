package com.netdisk.pojo.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建分享请求。
 */
public class CreateShareRequestDTO {
    @NotEmpty(message = "resourceIds不能为空")
    private List<String> resourceIds;
    private LocalDateTime expiredAt;
    private Boolean needCode;
    private String code;
    private Boolean allowPreview;
    private Boolean allowDownload;
    @Min(value = 1, message = "maxAccessCount必须大于0")
    private Integer maxAccessCount;

    public List<String> getResourceIds() {
        return resourceIds;
    }

    public void setResourceIds(List<String> resourceIds) {
        this.resourceIds = resourceIds;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public void setExpiredAt(LocalDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }

    public Boolean getNeedCode() {
        return needCode;
    }

    public void setNeedCode(Boolean needCode) {
        this.needCode = needCode;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Boolean getAllowPreview() {
        return allowPreview;
    }

    public void setAllowPreview(Boolean allowPreview) {
        this.allowPreview = allowPreview;
    }

    public Boolean getAllowDownload() {
        return allowDownload;
    }

    public void setAllowDownload(Boolean allowDownload) {
        this.allowDownload = allowDownload;
    }

    public Integer getMaxAccessCount() {
        return maxAccessCount;
    }

    public void setMaxAccessCount(Integer maxAccessCount) {
        this.maxAccessCount = maxAccessCount;
    }
}

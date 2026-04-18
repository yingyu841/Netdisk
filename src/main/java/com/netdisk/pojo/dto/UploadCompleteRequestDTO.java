package com.netdisk.pojo.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * 完成上传请求。
 */
public class UploadCompleteRequestDTO {
    @NotNull(message = "parts必填")
    @Valid
    private List<PartItem> parts = new ArrayList<PartItem>();

    public List<PartItem> getParts() {
        return parts;
    }

    public void setParts(List<PartItem> parts) {
        this.parts = parts;
    }

    public static class PartItem {
        private Integer partNumber;
        private String etag;

        public Integer getPartNumber() {
            return partNumber;
        }

        public void setPartNumber(Integer partNumber) {
            this.partNumber = partNumber;
        }

        public String getEtag() {
            return etag;
        }

        public void setEtag(String etag) {
            this.etag = etag;
        }
    }
}

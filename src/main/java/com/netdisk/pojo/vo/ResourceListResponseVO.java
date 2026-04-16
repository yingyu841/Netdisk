package com.netdisk.pojo.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 资源列表返回。
 */
public class ResourceListResponseVO {
    private long total;
    private List<ResourceItemVO> items = new ArrayList<ResourceItemVO>();

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<ResourceItemVO> getItems() {
        return items;
    }

    public void setItems(List<ResourceItemVO> items) {
        this.items = items;
    }
}

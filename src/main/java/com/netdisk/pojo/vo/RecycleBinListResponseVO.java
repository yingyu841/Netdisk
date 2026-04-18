package com.netdisk.pojo.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 回收站列表返回。
 */
public class RecycleBinListResponseVO {
    private long total;
    private List<RecycleBinItemVO> items = new ArrayList<RecycleBinItemVO>();

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<RecycleBinItemVO> getItems() {
        return items;
    }

    public void setItems(List<RecycleBinItemVO> items) {
        this.items = items;
    }
}

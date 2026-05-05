package com.netdisk.pojo.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作记录列表返回。
 */
public class ActivityListResponseVO {
    private long total;
    private List<ActivityItemVO> items = new ArrayList<ActivityItemVO>();

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<ActivityItemVO> getItems() {
        return items;
    }

    public void setItems(List<ActivityItemVO> items) {
        this.items = items;
    }
}

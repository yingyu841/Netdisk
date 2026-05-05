package com.netdisk.pojo.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录树节点。
 */
public class ResourceTreeNodeVO {
    private String id;
    private String parentId;
    private String name;
    private List<ResourceTreeNodeVO> children = new ArrayList<ResourceTreeNodeVO>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ResourceTreeNodeVO> getChildren() {
        return children;
    }

    public void setChildren(List<ResourceTreeNodeVO> children) {
        this.children = children;
    }
}

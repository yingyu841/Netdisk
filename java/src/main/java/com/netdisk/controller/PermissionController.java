package com.netdisk.controller;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.mapper.ResourceMapper;
import com.netdisk.pojo.dto.UpdateAclRequestDTO;
import com.netdisk.pojo.entity.Resource;
import com.netdisk.pojo.entity.ResourceAcl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源权限接口。
 */
@RestController
public class PermissionController {
    private final ResourceMapper resourceMapper;

    public PermissionController(ResourceMapper resourceMapper) {
        this.resourceMapper = resourceMapper;
    }

    /**
     * 查询资源 ACL。
     */
    @GetMapping("/api/v1/resources/{resourceId}/acl")
    public ApiResponse<Map<String, Object>> getAcl(
            @PathVariable String resourceId,
            HttpServletRequest req) {
        Resource resource = requireOwnedResource(req, resourceId);
        List<ResourceAcl> aclRows = resourceMapper.listAclByResourceId(resource.getId());

        List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
        if (aclRows != null) {
            for (ResourceAcl row : aclRows) {
                entries.add(toAclItem(row));
            }
        }
        if (entries.isEmpty()) {
            entries.add(defaultOwnerAcl(String.valueOf(req.getAttribute("authUserId"))));
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("inheritFlag", true);
        data.put("entries", entries);
        return ApiResponse.ok(data, requestId(req));
    }

    /**
     * 更新资源 ACL。
     */
    @PutMapping("/api/v1/resources/{resourceId}/acl")
    @Transactional
    public ApiResponse<Map<String, Object>> putAcl(
            @PathVariable String resourceId,
            @RequestBody(required = false) UpdateAclRequestDTO request,
            HttpServletRequest req) {
        Resource resource = requireOwnedResource(req, resourceId);
        if (request == null || request.getEntries() == null || request.getEntries().isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "entries 不能为空");
        }
        boolean inheritFlag = request.getInheritFlag() == null || request.getInheritFlag().booleanValue();

        resourceMapper.deleteAclByResourceId(resource.getId());
        List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
        for (UpdateAclRequestDTO.Entry entry : request.getEntries()) {
            String subjectType = normalize(entry == null ? null : entry.getSubjectType());
            String subjectId = normalize(entry == null ? null : entry.getSubjectId());
            if (!"user".equals(subjectType) && !"space_role".equals(subjectType)) {
                throw new BizException(ErrorCode.INVALID_PARAM, 400, "subjectType 仅支持 user 或 space_role");
            }
            if (subjectId.isEmpty()) {
                throw new BizException(ErrorCode.INVALID_PARAM, 400, "subjectId 不能为空");
            }
            ResourceAcl acl = new ResourceAcl();
            acl.setResourceId(resource.getId());
            acl.setSubjectType(subjectType);
            acl.setSubjectId(subjectId);
            acl.setCanRead(toBit(entry.getCanRead(), false));
            acl.setCanWrite(toBit(entry.getCanWrite(), false));
            acl.setCanDelete(toBit(entry.getCanDelete(), false));
            acl.setCanShare(toBit(entry.getCanShare(), false));
            acl.setCanDownload(toBit(entry.getCanDownload(), false));
            acl.setCanManageAcl(toBit(entry.getCanManageAcl(), false));
            acl.setInheritFlag(toBit(Boolean.valueOf(inheritFlag), true));
            acl.setCreatedByUserId(resource.getOwnerUserId());
            resourceMapper.insertResourceAcl(acl);
            entries.add(toAclItem(acl));
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("inheritFlag", inheritFlag);
        data.put("entries", entries);
        return ApiResponse.ok(data, requestId(req));
    }

    private Resource requireOwnedResource(HttpServletRequest req, String resourceId) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        Resource resource = resourceMapper.findByResourceUuid(userUuid, normalize(resourceId));
        if (resource == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "资源不存在");
        }
        return resource;
    }

    private Map<String, Object> toAclItem(ResourceAcl acl) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("subjectType", acl.getSubjectType());
        item.put("subjectId", acl.getSubjectId());
        item.put("canRead", toBool(acl.getCanRead(), true));
        item.put("canWrite", toBool(acl.getCanWrite(), true));
        item.put("canDelete", toBool(acl.getCanDelete(), true));
        item.put("canShare", toBool(acl.getCanShare(), true));
        item.put("canDownload", toBool(acl.getCanDownload(), true));
        item.put("canManageAcl", toBool(acl.getCanManageAcl(), true));
        item.put("inherit", toBool(acl.getInheritFlag(), true));
        return item;
    }

    private Map<String, Object> defaultOwnerAcl(String userUuid) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("subjectType", "user");
        item.put("subjectId", normalize(userUuid));
        item.put("canRead", true);
        item.put("canWrite", true);
        item.put("canDelete", true);
        item.put("canShare", true);
        item.put("canDownload", true);
        item.put("canManageAcl", true);
        item.put("inherit", true);
        return item;
    }

    private int toBit(Boolean value, boolean defaultValue) {
        boolean finalValue = value == null ? defaultValue : value.booleanValue();
        return finalValue ? 1 : 0;
    }

    private boolean toBool(Integer value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value.intValue() != 0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 从请求上下文读取请求ID。
     */
    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}

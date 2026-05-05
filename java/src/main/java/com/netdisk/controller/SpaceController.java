package com.netdisk.controller;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.mapper.UserMapper;
import com.netdisk.pojo.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 空间相关接口。
 */
@RestController
public class SpaceController {
    private final UserMapper userMapper;

    public SpaceController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 获取当前用户空间信息。
     */
    @GetMapping("/api/v1/spaces/current")
    public ApiResponse<Map<String, Object>> currentSpace(HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        User user = userMapper.findByUserUuid(userUuid);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", "space_personal_" + user.getUserUuid());
        data.put("name", resolveSpaceName(user.getNickname()));
        data.put("spaceType", "personal");
        data.put("role", "owner");
        return ApiResponse.ok(data, requestId(req));
    }

    private String resolveSpaceName(String nickname) {
        String name = nickname == null ? "" : nickname.trim();
        if (name.isEmpty()) {
            return "我的网盘";
        }
        return name + "的网盘";
    }

    /**
     * 从请求上下文读取请求ID。
     */
    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}

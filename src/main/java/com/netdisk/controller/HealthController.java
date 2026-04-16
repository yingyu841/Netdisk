package com.netdisk.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口。
 */
@RestController
public class HealthController {
    /**
     * 服务健康检查。
     */
    @GetMapping("/healthz")
    public Map<String, String> health() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("status", "ok");
        return map;
    }
}

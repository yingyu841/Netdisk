package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.pojo.dto.AiAgentConfigDTO;
import com.netdisk.pojo.dto.AiChatRequestDTO;
import com.netdisk.pojo.vo.AiChatResponseVO;
import com.netdisk.pojo.vo.AiConversationVO;
import com.netdisk.service.AiAgentService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/ai")
public class AiAgentController {
    private final AiAgentService aiAgentService;

    public AiAgentController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponseVO> chat(@Valid @RequestBody AiChatRequestDTO request, HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        return ApiResponse.ok(aiAgentService.chat(userUuid, request), requestId(req));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<AiConversationVO>> listConversations(HttpServletRequest req) {
        return ApiResponse.ok(aiAgentService.listConversations(String.valueOf(req.getAttribute("authUserId"))),
                requestId(req));
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<AiConversationVO> getConversation(@PathVariable String conversationId, HttpServletRequest req) {
        return ApiResponse.ok(
                aiAgentService.getConversation(String.valueOf(req.getAttribute("authUserId")), conversationId),
                requestId(req));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Map<String, Object>> deleteConversation(@PathVariable String conversationId,
            HttpServletRequest req) {
        aiAgentService.deleteConversation(conversationId);
        Map<String, Object> data = new HashMap<>();
        data.put("deleted", true);
        return ApiResponse.ok(data, requestId(req));
    }

    @GetMapping("/config")
    public ApiResponse<AiAgentConfigDTO> getConfig(HttpServletRequest req) {
        return ApiResponse.ok(aiAgentService.getConfig(String.valueOf(req.getAttribute("authUserId"))), requestId(req));
    }

    @PutMapping("/config")
    public ApiResponse<AiAgentConfigDTO> updateConfig(@RequestBody AiAgentConfigDTO dto, HttpServletRequest req) {
        String userUuid = String.valueOf(req.getAttribute("authUserId"));
        aiAgentService.updateConfig(userUuid, dto);
        return ApiResponse.ok(aiAgentService.getConfig(userUuid), requestId(req));
    }

    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}

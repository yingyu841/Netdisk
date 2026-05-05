package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.pojo.dto.chat.AddMembersRequestDTO;
import com.netdisk.pojo.dto.chat.CreateConversationRequestDTO;
import com.netdisk.pojo.dto.chat.EditMessageRequestDTO;
import com.netdisk.pojo.dto.chat.MuteConversationRequestDTO;
import com.netdisk.pojo.dto.chat.PinMessageRequestDTO;
import com.netdisk.pojo.dto.chat.ReadBatchRequestDTO;
import com.netdisk.pojo.dto.chat.SendMessageRequestDTO;
import com.netdisk.pojo.dto.chat.UpdateConversationRequestDTO;
import com.netdisk.service.ChatService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/conversations")
    public ApiResponse<Map<String, Object>> createConversation(
            @Valid @RequestBody CreateConversationRequestDTO request,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.createConversation(currentUser(req), toMap(request)), requestId(req));
    }

    @GetMapping("/conversations")
    public ApiResponse<Map<String, Object>> listConversations(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.listConversations(currentUser(req), keyword, page, pageSize), requestId(req));
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<Map<String, Object>> getConversation(@PathVariable String conversationId, HttpServletRequest req) {
        return ApiResponse.ok(chatService.getConversation(currentUser(req), conversationId), requestId(req));
    }

    @PatchMapping("/conversations/{conversationId}")
    public ApiResponse<Map<String, Object>> updateConversation(
            @PathVariable String conversationId,
            @RequestBody(required = false) @Valid UpdateConversationRequestDTO request,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.updateConversation(currentUser(req), conversationId, toMap(request)), requestId(req));
    }

    @PostMapping("/conversations/{conversationId}/archive")
    public ApiResponse<Map<String, Object>> archiveConversation(@PathVariable String conversationId, HttpServletRequest req) {
        return ApiResponse.ok(chatService.archiveConversation(currentUser(req), conversationId), requestId(req));
    }

    @GetMapping("/conversations/{conversationId}/members")
    public ApiResponse<Map<String, Object>> listMembers(
            @PathVariable String conversationId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.listMembers(currentUser(req), conversationId, page, pageSize), requestId(req));
    }

    @PostMapping("/conversations/{conversationId}/members")
    public ApiResponse<Map<String, Object>> addMembers(
            @PathVariable String conversationId,
            @Valid @RequestBody AddMembersRequestDTO request,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.addMembers(currentUser(req), conversationId, toMap(request)), requestId(req));
    }

    @DeleteMapping("/conversations/{conversationId}/members/{userId}")
    public ApiResponse<Map<String, Object>> removeMember(
            @PathVariable String conversationId,
            @PathVariable("userId") String targetUserId,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.removeMember(currentUser(req), conversationId, targetUserId), requestId(req));
    }

    @PostMapping("/conversations/{conversationId}/mute")
    public ApiResponse<Map<String, Object>> muteConversation(
            @PathVariable String conversationId,
            @RequestBody(required = false) @Valid MuteConversationRequestDTO request,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.muteConversation(currentUser(req), conversationId, toMap(request)), requestId(req));
    }

    @PostMapping("/conversations/{conversationId}/unmute")
    public ApiResponse<Map<String, Object>> unmuteConversation(@PathVariable String conversationId, HttpServletRequest req) {
        return ApiResponse.ok(chatService.unmuteConversation(currentUser(req), conversationId), requestId(req));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<Map<String, Object>> sendMessage(
            @PathVariable String conversationId,
            @Valid @RequestBody SendMessageRequestDTO request,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.sendMessage(currentUser(req), conversationId, toMap(request)), requestId(req));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<Map<String, Object>> listMessages(
            @PathVariable String conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "30") Integer limit,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.listMessages(currentUser(req), conversationId, cursor, limit), requestId(req));
    }

    @PatchMapping("/messages/{messageId}")
    public ApiResponse<Map<String, Object>> editMessage(
            @PathVariable String messageId,
            @Valid @RequestBody EditMessageRequestDTO request,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.editMessage(currentUser(req), messageId, toMap(request)), requestId(req));
    }

    @DeleteMapping("/messages/{messageId}")
    public ApiResponse<Map<String, Object>> recallMessage(@PathVariable String messageId, HttpServletRequest req) {
        return ApiResponse.ok(chatService.recallMessage(currentUser(req), messageId), requestId(req));
    }

    @PostMapping("/messages/{messageId}/read")
    public ApiResponse<Map<String, Object>> markRead(@PathVariable String messageId, HttpServletRequest req) {
        return ApiResponse.ok(chatService.markRead(currentUser(req), messageId), requestId(req));
    }

    @PostMapping("/messages/read-batch")
    public ApiResponse<Map<String, Object>> markReadBatch(
            @Valid @RequestBody ReadBatchRequestDTO request,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.markReadBatch(currentUser(req), toMap(request)), requestId(req));
    }

    @GetMapping("/conversations/{conversationId}/unread")
    public ApiResponse<Map<String, Object>> unreadCount(@PathVariable String conversationId, HttpServletRequest req) {
        return ApiResponse.ok(chatService.unreadCount(currentUser(req), conversationId), requestId(req));
    }

    @GetMapping("/conversations/{conversationId}/pins")
    public ApiResponse<Map<String, Object>> listPins(@PathVariable String conversationId, HttpServletRequest req) {
        return ApiResponse.ok(chatService.listPins(currentUser(req), conversationId), requestId(req));
    }

    @PostMapping("/conversations/{conversationId}/pins")
    public ApiResponse<Map<String, Object>> pinMessage(
            @PathVariable String conversationId,
            @Valid @RequestBody PinMessageRequestDTO request,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.pinMessage(currentUser(req), conversationId, toMap(request)), requestId(req));
    }

    @DeleteMapping("/conversations/{conversationId}/pins/{messageId}")
    public ApiResponse<Map<String, Object>> unpinMessage(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            HttpServletRequest req) {
        return ApiResponse.ok(chatService.unpinMessage(currentUser(req), conversationId, messageId), requestId(req));
    }

    @GetMapping("/ws-token")
    public ApiResponse<Map<String, Object>> wsToken(HttpServletRequest req) {
        return ApiResponse.ok(chatService.wsToken(currentUser(req)), requestId(req));
    }

    private Map<String, Object> toMap(CreateConversationRequestDTO request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("conversationType", request.getConversationType());
        body.put("spaceId", request.getSpaceId());
        body.put("name", request.getName());
        body.put("avatarUrl", request.getAvatarUrl());
        body.put("memberUserIds", copyList(request.getMemberUserIds()));
        return body;
    }

    private Map<String, Object> toMap(UpdateConversationRequestDTO request) {
        if (request == null) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        if (request.getName() != null) {
            body.put("name", request.getName());
        }
        if (request.getAvatarUrl() != null) {
            body.put("avatarUrl", request.getAvatarUrl());
        }
        return body;
    }

    private Map<String, Object> toMap(AddMembersRequestDTO request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("userIds", copyList(request.getUserIds()));
        return body;
    }

    private Map<String, Object> toMap(MuteConversationRequestDTO request) {
        if (request == null) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("muteUntil", request.getMuteUntil());
        return body;
    }

    private Map<String, Object> toMap(SendMessageRequestDTO request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("messageType", request.getMessageType());
        body.put("contentText", request.getContentText());
        body.put("content", request.getContent());
        body.put("replyToMessageId", request.getReplyToMessageId());
        body.put("resourceId", request.getResourceId());
        body.put("clientMessageId", request.getClientMessageId());
        return body;
    }

    private Map<String, Object> toMap(EditMessageRequestDTO request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("contentText", request.getContentText());
        return body;
    }

    private Map<String, Object> toMap(ReadBatchRequestDTO request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("conversationId", request.getConversationId());
        body.put("messageIds", copyList(request.getMessageIds()));
        return body;
    }

    private Map<String, Object> toMap(PinMessageRequestDTO request) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("messageId", request.getMessageId());
        return body;
    }

    private List<String> copyList(List<String> source) {
        return source == null ? new ArrayList<String>() : new ArrayList<String>(source);
    }

    private String currentUser(HttpServletRequest req) {
        Object uid = req.getAttribute("authUserId");
        return uid == null ? "" : String.valueOf(uid);
    }

    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}

package com.netdisk.service;

import com.netdisk.websocket.ChatWebSocketHandler;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatMessagePushService {
    private final ChatWebSocketHandler chatWebSocketHandler;

    public ChatMessagePushService(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    public void pushNewMessage(String conversationUuid, String conversationType, List<Long> memberUserIds,
            Map<String, Object> message) {
        Map<String, Map<String, Object>> userMessages = new HashMap<>();
        for (Long userId : memberUserIds) {
            String userIdStr = String.valueOf(userId);
            Map<String, Object> pushData = new HashMap<>();
            pushData.put("type", "new_message");
            pushData.put("conversationId", conversationUuid);
            pushData.put("conversationType", conversationType);
            pushData.put("message", message);
            userMessages.put(userIdStr, pushData);
        }
        if (chatWebSocketHandler != null) {
            chatWebSocketHandler.pushToUsers(userMessages);
        }
    }

    public void pushMessageRecall(String conversationUuid, List<Long> memberUserIds, String messageId) {
        for (Long userId : memberUserIds) {
            String userIdStr = String.valueOf(userId);
            Map<String, Object> pushData = new HashMap<>();
            pushData.put("type", "message_recalled");
            pushData.put("conversationId", conversationUuid);
            pushData.put("messageId", messageId);
            if (chatWebSocketHandler != null) {
                chatWebSocketHandler.pushToUser(userIdStr, pushData);
            }
        }
    }

    public void pushMemberChange(String conversationUuid, List<Long> memberUserIds, String eventType, String userId) {
        for (Long userIdVal : memberUserIds) {
            String targetUserId = String.valueOf(userIdVal);
            Map<String, Object> pushData = new HashMap<>();
            pushData.put("type", eventType);
            pushData.put("conversationId", conversationUuid);
            pushData.put("userId", userId);
            if (chatWebSocketHandler != null) {
                chatWebSocketHandler.pushToUser(targetUserId, pushData);
            }
        }
    }

    public void pushConversationUpdate(String conversationUuid, List<Long> memberUserIds,
            Map<String, Object> updateData) {
        for (Long userId : memberUserIds) {
            String userIdStr = String.valueOf(userId);
            Map<String, Object> pushData = new HashMap<>();
            pushData.put("type", "conversation_updated");
            pushData.put("conversationId", conversationUuid);
            pushData.put("data", updateData);
            if (chatWebSocketHandler != null) {
                chatWebSocketHandler.pushToUser(userIdStr, pushData);
            }
        }
    }
}

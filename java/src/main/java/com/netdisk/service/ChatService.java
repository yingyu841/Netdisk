package com.netdisk.service;

import java.util.Map;

public interface ChatService {
    Map<String, Object> createConversation(String userUuid, Map<String, Object> request);

    Map<String, Object> listConversations(String userUuid, String keyword, Integer page, Integer pageSize);

    Map<String, Object> getConversation(String userUuid, String conversationId);

    Map<String, Object> updateConversation(String userUuid, String conversationId, Map<String, Object> request);

    Map<String, Object> archiveConversation(String userUuid, String conversationId);

    Map<String, Object> listMembers(String userUuid, String conversationId, Integer page, Integer pageSize);

    Map<String, Object> addMembers(String userUuid, String conversationId, Map<String, Object> request);

    Map<String, Object> removeMember(String userUuid, String conversationId, String targetUserUuid);

    Map<String, Object> muteConversation(String userUuid, String conversationId, Map<String, Object> request);

    Map<String, Object> unmuteConversation(String userUuid, String conversationId);

    Map<String, Object> sendMessage(String userUuid, String conversationId, Map<String, Object> request);

    Map<String, Object> listMessages(String userUuid, String conversationId, String cursor, Integer limit);

    Map<String, Object> editMessage(String userUuid, String messageId, Map<String, Object> request);

    Map<String, Object> recallMessage(String userUuid, String messageId);

    Map<String, Object> markRead(String userUuid, String messageId);

    Map<String, Object> markReadBatch(String userUuid, Map<String, Object> request);

    Map<String, Object> unreadCount(String userUuid, String conversationId);

    Map<String, Object> listPins(String userUuid, String conversationId);

    Map<String, Object> pinMessage(String userUuid, String conversationId, Map<String, Object> request);

    Map<String, Object> unpinMessage(String userUuid, String conversationId, String messageId);

    Map<String, Object> wsToken(String userUuid);
}

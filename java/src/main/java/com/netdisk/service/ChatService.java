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

    Map<String, Object> leaveConversation(String userUuid, String conversationId);

    Map<String, Object> dissolveConversation(String userUuid, String conversationId);

    Map<String, Object> transferOwnership(String userUuid, String conversationId, Map<String, Object> request);

    Map<String, Object> setAdmin(String userUuid, String conversationId, Map<String, Object> request);

    Map<String, Object> removeAdmin(String userUuid, String conversationId, Map<String, Object> request);

    Map<String, Object> unarchiveConversation(String userUuid, String conversationId);

    Map<String, Object> setAnnouncement(String userUuid, String conversationId, Map<String, Object> request);

    Map<String, Object> getAnnouncement(String userUuid, String conversationId);

    // ==================== 群聊邀请 ====================
    Map<String, Object> createInvite(String userUuid, String conversationId, Map<String, Object> request);

    Map<String, Object> listInvites(String userUuid, String conversationId, String status, Integer page, Integer pageSize);

    Map<String, Object> getInvite(String userUuid, String inviteId);

    Map<String, Object> acceptInvite(String userUuid, String inviteToken);

    Map<String, Object> rejectInvite(String userUuid, String inviteId);

    Map<String, Object> cancelInvite(String userUuid, String inviteId);

    Map<String, Object> listReceivedInvites(String userUuid, String status, Integer page, Integer pageSize);
}

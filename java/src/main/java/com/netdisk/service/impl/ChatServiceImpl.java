package com.netdisk.service.impl;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.mapper.UserMapper;
import com.netdisk.messaging.chat.ChatOutboxService;
import com.netdisk.messaging.chat.ChatMessageSentEvent;
import com.netdisk.messaging.chat.ChatReadBatchEvent;
import com.netdisk.pojo.entity.User;
import com.netdisk.service.ChatService;
import com.netdisk.service.chat.ChatCacheIndexService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ChatServiceImpl implements ChatService {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long CONVERSATION_LIST_CACHE_SECONDS = 20L;
    private static final long CONVERSATION_DETAIL_CACHE_SECONDS = 20L;
    private static final long UNREAD_CACHE_SECONDS = 10L;
    private static final long UNREAD_COUNTER_CACHE_SECONDS = 86400L;
    private static final long MESSAGE_LIST_CACHE_SECONDS = 8L;
    private static final long MEMBER_LIST_CACHE_SECONDS = 20L;
    private static final long PIN_LIST_CACHE_SECONDS = 15L;
    private static final long WS_TOKEN_CACHE_SECONDS = 300L;
    private static final long MESSAGE_DEDUP_CACHE_SECONDS = 86400L;

    private final JdbcTemplate jdbcTemplate;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final ChatOutboxService chatOutboxService;
    private final ChatCacheIndexService chatCacheIndexService;

    public ChatServiceImpl(
            JdbcTemplate jdbcTemplate,
            UserMapper userMapper,
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> redisProvider,
            ChatOutboxService chatOutboxService,
            ChatCacheIndexService chatCacheIndexService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisProvider.getIfAvailable();
        this.chatOutboxService = chatOutboxService;
        this.chatCacheIndexService = chatCacheIndexService;
    }

    @Override
    @Transactional
    public Map<String, Object> createConversation(String userUuid, Map<String, Object> request) {
        User user = requireUser(userUuid);
        String type = normalizeConversationType(str(request, "conversationType"));
        List<String> memberUuids = strList(request.get("memberUserIds"));
        if (memberUuids.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "memberUserIds不能为空");
        }

        String convUuid = UUID.randomUUID().toString();
        String name = trim(str(request, "name"));
        String avatarUrl = trim(str(request, "avatarUrl"));
        Long spaceId = resolveSpaceId(request.get("spaceId"));
        Long ownerId = "group".equals(type) ? user.getId() : null;

        jdbcTemplate.update(
                "INSERT INTO chat_conversations (conversation_uuid, space_id, conversation_type, name, avatar_url, created_by_user_id, owner_user_id, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'active', NOW(), NOW())",
                convUuid, spaceId, type, emptyToNull(name), emptyToNull(avatarUrl), user.getId(), ownerId
        );
        Long convId = jdbcTemplate.queryForObject(
                "SELECT id FROM chat_conversations WHERE conversation_uuid = ?",
                Long.class,
                convUuid
        );

        addOrReactivateMember(convId, user.getId(), "owner");
        int added = 1;
        for (String memberUuid : memberUuids) {
            User member = userMapper.findByUserUuid(memberUuid);
            if (member == null) {
                continue;
            }
            if (member.getId().longValue() == user.getId().longValue()) {
                continue;
            }
            addOrReactivateMember(convId, member.getId(), "member");
            added++;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("conversationType", type);
        payload.put("memberCount", added);
        payload.put("name", name);
        logConversationEvent(convId, user.getId(), "create", payload);
        syncUnreadCounterForAllMembers(convId, convUuid);
        evictConversationRelatedCache(convId);

        return getConversation(userUuid, convUuid);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listConversations(String userUuid, String keyword, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        int[] normalized = normalizePage(page, pageSize, 200);
        int offset = (normalized[0] - 1) * normalized[1];
        String kw = normalizeLike(keyword);
        String cacheKey = conversationListCacheKey(user.getId(), kw, normalized[0], normalized[1]);
        Map<String, Object> cached = readMapCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) " +
                        "FROM chat_conversation_members m " +
                        "JOIN chat_conversations c ON c.id = m.conversation_id " +
                        "WHERE m.user_id = ? AND m.status = 'active' " +
                        "AND c.status IN ('active','archived') " +
                        "AND (? IS NULL OR c.name LIKE ?)",
                Long.class, user.getId(), kw, kw
        );
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.id, c.conversation_uuid, c.conversation_type, c.name, c.avatar_url, c.status, c.last_message_at, " +
                        "m.is_muted, m.mute_until, m.last_read_message_id, " +
                        "lm.message_uuid last_message_uuid, lm.message_type last_message_type, lm.content_text last_message_content_text, lm.created_at last_message_created_at, " +
                        "(SELECT COUNT(1) FROM chat_messages um WHERE um.conversation_id = c.id AND um.id > IFNULL(m.last_read_message_id, 0)) unread_count " +
                        "FROM chat_conversation_members m " +
                        "JOIN chat_conversations c ON c.id = m.conversation_id " +
                        "LEFT JOIN chat_messages lm ON lm.id = c.last_message_id " +
                        "WHERE m.user_id = ? AND m.status = 'active' " +
                        "AND c.status IN ('active','archived') " +
                        "AND (? IS NULL OR c.name LIKE ?) " +
                        "ORDER BY c.last_message_at DESC, c.updated_at DESC, c.id DESC LIMIT ?, ?",
                user.getId(), kw, kw, offset, normalized[1]
        );

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", str(row.get("conversation_uuid")));
            item.put("conversationType", str(row.get("conversation_type")));
            item.put("name", str(row.get("name")));
            item.put("avatarUrl", str(row.get("avatar_url")));
            item.put("status", str(row.get("status")));
            item.put("lastMessageAt", dt(row.get("last_message_at")));
            item.put("isMuted", boolInt(row.get("is_muted")));
            item.put("muteUntil", dt(row.get("mute_until")));
            item.put("unreadCount", longVal(row.get("unread_count")));
            item.put("lastMessage", buildLastMessageSummary(row));
            items.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        writeMapCache(
                cacheKey,
                data,
                CONVERSATION_LIST_CACHE_SECONDS,
                chatCacheIndexService.singleIndex(chatCacheIndexService.userListIndexKey(user.getId()))
        );
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getConversation(String userUuid, String conversationId) {
        User user = requireUser(userUuid);
        String cacheKey = conversationDetailCacheKey(user.getId(), conversationId);
        Map<String, Object> cached = readMapCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        Long convPk = asLong(conv.get("id"));

        List<Map<String, Object>> memberRows = jdbcTemplate.queryForList(
                "SELECT u.user_uuid, u.nickname, u.avatar_url, m.member_role, m.status, m.joined_at " +
                        "FROM chat_conversation_members m " +
                        "JOIN users u ON u.id = m.user_id " +
                        "WHERE m.conversation_id = ? AND m.status = 'active' ORDER BY m.id ASC",
                convPk
        );
        List<Map<String, Object>> members = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : memberRows) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("userId", str(row.get("user_uuid")));
            m.put("nickname", str(row.get("nickname")));
            m.put("avatarUrl", str(row.get("avatar_url")));
            m.put("role", str(row.get("member_role")));
            m.put("joinedAt", dt(row.get("joined_at")));
            members.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", str(conv.get("conversation_uuid")));
        data.put("conversationType", str(conv.get("conversation_type")));
        data.put("name", str(conv.get("name")));
        data.put("avatarUrl", str(conv.get("avatar_url")));
        data.put("status", str(conv.get("status")));
        data.put("spaceId", conv.get("space_id"));
        data.put("ownerUserId", conv.get("owner_user_uuid"));
        data.put("isMuted", boolInt(conv.get("is_muted")));
        data.put("muteUntil", dt(conv.get("mute_until")));
        data.put("lastMessageAt", dt(conv.get("last_message_at")));
        data.put("members", members);
        writeMapCache(
                cacheKey,
                data,
                CONVERSATION_DETAIL_CACHE_SECONDS,
                chatCacheIndexService.singleIndex(chatCacheIndexService.conversationUserIndexKey(user.getId(), str(conv.get("conversation_uuid"))))
        );
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> updateConversation(String userUuid, String conversationId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        ensureCanManageConversation(conv, user.getId());

        String name = trim(str(request, "name"));
        String avatarUrl = trim(str(request, "avatarUrl"));
        List<String> clauses = new ArrayList<String>();
        List<Object> args = new ArrayList<Object>();
        if (!name.isEmpty()) {
            clauses.add("name = ?");
            args.add(name);
        }
        if (request.containsKey("avatarUrl")) {
            clauses.add("avatar_url = ?");
            args.add(emptyToNull(avatarUrl));
        }
        if (clauses.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "至少提供一个可更新字段");
        }
        clauses.add("updated_at = NOW()");
        args.add(conv.get("id"));
        jdbcTemplate.update("UPDATE chat_conversations SET " + joinClauses(clauses) + " WHERE id = ?", args.toArray());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", name);
        payload.put("avatarUrl", avatarUrl);
        logConversationEvent(asLong(conv.get("id")), user.getId(), "rename", payload);
        evictConversationRelatedCache(asLong(conv.get("id")));
        return getConversation(userUuid, conversationId);
    }

    @Override
    @Transactional
    public Map<String, Object> archiveConversation(String userUuid, String conversationId) {
        User user = requireUser(userUuid);
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        ensureCanManageConversation(conv, user.getId());
        jdbcTemplate.update(
                "UPDATE chat_conversations SET status = 'archived', updated_at = NOW() WHERE id = ?",
                conv.get("id")
        );
        logConversationEvent(asLong(conv.get("id")), user.getId(), "archive", null);
        evictConversationRelatedCache(asLong(conv.get("id")));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("conversationId", conversationId);
        data.put("archived", true);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listMembers(String userUuid, String conversationId, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        int[] normalized = normalizePage(page, pageSize, 200);
        int offset = (normalized[0] - 1) * normalized[1];
        String cacheKey = memberListCacheKey(user.getId(), str(conv.get("conversation_uuid")), normalized[0], normalized[1]);
        Map<String, Object> cached = readMapCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM chat_conversation_members WHERE conversation_id = ? AND status = 'active'",
                Long.class,
                conv.get("id")
        );
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT u.user_uuid, u.nickname, u.avatar_url, m.member_role, m.joined_at, m.is_muted, m.mute_until " +
                        "FROM chat_conversation_members m " +
                        "JOIN users u ON u.id = m.user_id " +
                        "WHERE m.conversation_id = ? AND m.status = 'active' " +
                        "ORDER BY m.id ASC LIMIT ?, ?",
                conv.get("id"), offset, normalized[1]
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("userId", str(row.get("user_uuid")));
            item.put("nickname", str(row.get("nickname")));
            item.put("avatarUrl", str(row.get("avatar_url")));
            item.put("role", str(row.get("member_role")));
            item.put("joinedAt", dt(row.get("joined_at")));
            item.put("isMuted", boolInt(row.get("is_muted")));
            item.put("muteUntil", dt(row.get("mute_until")));
            items.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        writeMapCache(
                cacheKey,
                data,
                MEMBER_LIST_CACHE_SECONDS,
                chatCacheIndexService.singleIndex(chatCacheIndexService.conversationUserIndexKey(user.getId(), str(conv.get("conversation_uuid"))))
        );
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> addMembers(String userUuid, String conversationId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        ensureCanManageConversation(conv, user.getId());
        List<String> userIds = strList(request.get("userIds"));
        if (userIds.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "userIds不能为空");
        }

        int added = 0;
        List<String> addedUsers = new ArrayList<String>();
        for (String targetUuid : userIds) {
            User target = userMapper.findByUserUuid(targetUuid);
            if (target == null) {
                continue;
            }
            addOrReactivateMember(asLong(conv.get("id")), target.getId(), "member");
            added++;
            addedUsers.add(targetUuid);
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("userIds", addedUsers);
        payload.put("addedCount", added);
        logConversationEvent(asLong(conv.get("id")), user.getId(), "member_add", payload);
        for (String addedUserUuid : addedUsers) {
            User addedUser = userMapper.findByUserUuid(addedUserUuid);
            if (addedUser != null) {
                setUnreadCounter(str(conv.get("conversation_uuid")), addedUser.getId(), 0L);
            }
        }
        evictConversationRelatedCache(asLong(conv.get("id")));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("conversationId", conversationId);
        data.put("addedCount", added);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> removeMember(String userUuid, String conversationId, String targetUserUuid) {
        User user = requireUser(userUuid);
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        ensureCanManageConversation(conv, user.getId());
        User target = requireUser(targetUserUuid);

        jdbcTemplate.update(
                "UPDATE chat_conversation_members SET status = 'removed', left_at = NOW(), updated_at = NOW() " +
                        "WHERE conversation_id = ? AND user_id = ? AND status = 'active'",
                conv.get("id"), target.getId()
        );
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("userId", targetUserUuid);
        logConversationEvent(asLong(conv.get("id")), user.getId(), "member_remove", payload);
        deleteUnreadCounter(str(conv.get("conversation_uuid")), target.getId());
        evictConversationRelatedCache(asLong(conv.get("id")));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("conversationId", conversationId);
        data.put("removedUserId", targetUserUuid);
        data.put("removed", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> muteConversation(String userUuid, String conversationId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        String muteUntil = trim(str(request, "muteUntil"));
        LocalDateTime until = parseOptionalDateTime(muteUntil);

        jdbcTemplate.update(
                "UPDATE chat_conversation_members SET is_muted = 1, mute_until = ?, updated_at = NOW() WHERE conversation_id = ? AND user_id = ?",
                until, conv.get("id"), user.getId()
        );
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("muteUntil", until == null ? null : FMT.format(until));
        logConversationEvent(asLong(conv.get("id")), user.getId(), "mute", payload);
        evictConversationRelatedCache(asLong(conv.get("id")));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("conversationId", conversationId);
        data.put("isMuted", true);
        data.put("muteUntil", until == null ? null : FMT.format(until));
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> unmuteConversation(String userUuid, String conversationId) {
        User user = requireUser(userUuid);
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        jdbcTemplate.update(
                "UPDATE chat_conversation_members SET is_muted = 0, mute_until = NULL, updated_at = NOW() WHERE conversation_id = ? AND user_id = ?",
                conv.get("id"), user.getId()
        );
        logConversationEvent(asLong(conv.get("id")), user.getId(), "unmute", null);
        evictConversationRelatedCache(asLong(conv.get("id")));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("conversationId", conversationId);
        data.put("isMuted", false);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> sendMessage(String userUuid, String conversationId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        String conversationUuid = str(conv.get("conversation_uuid"));
        String messageType = normalizeMessageType(str(request, "messageType"));
        String contentText = trim(str(request, "contentText"));
        String contentJson = jsonString(request.get("content"));
        String clientMessageId = trim(str(request, "clientMessageId"));
        if (!clientMessageId.isEmpty()) {
            String dedupMessageUuid = readMessageDedup(conversationUuid, user.getId(), clientMessageId);
            if (!dedupMessageUuid.isEmpty()) {
                return messageByUuid(dedupMessageUuid);
            }
        }
        Long replyToId = resolveMessageId(trim(str(request, "replyToMessageId")), false);
        Long resourceId = asLong(request.get("resourceId"));
        String messageUuid = UUID.randomUUID().toString();

        jdbcTemplate.update(
                "INSERT INTO chat_messages (message_uuid, conversation_id, sender_user_id, message_type, content_text, content_json, reply_to_message_id, resource_id, client_message_id, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, 'sent', NOW(), NOW())",
                messageUuid, conv.get("id"), user.getId(), messageType, emptyToNull(contentText), contentJson, replyToId, resourceId, emptyToNull(clientMessageId)
        );
        Long msgId = jdbcTemplate.queryForObject("SELECT id FROM chat_messages WHERE message_uuid = ?", Long.class, messageUuid);

        if (!clientMessageId.isEmpty()) {
            writeMessageDedup(conversationUuid, user.getId(), clientMessageId, messageUuid);
        }
        ChatMessageSentEvent event = new ChatMessageSentEvent();
        event.setConversationId(asLong(conv.get("id")));
        event.setConversationUuid(conversationUuid);
        event.setSenderUserId(user.getId());
        event.setMessageId(msgId);
        event.setMessageUuid(messageUuid);
        event.setMessageType(messageType);
        chatOutboxService.enqueueMessageSent(event);
        return messageByUuid(messageUuid);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listMessages(String userUuid, String conversationId, String cursor, Integer limit) {
        User user = requireUser(userUuid);
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        int size = limit == null ? 30 : Math.max(1, Math.min(100, limit.intValue()));
        Long cursorId = resolveMessageId(cursor, false);
        String cacheKey = messageListCacheKey(user.getId(), str(conv.get("conversation_uuid")), cursorId, size);
        Map<String, Object> cached = readMapCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Map<String, Object>> rows;
        if (cursorId == null) {
            rows = jdbcTemplate.queryForList(
                    "SELECT m.id internal_id, m.message_uuid, m.message_type, m.content_text, m.content_json, m.status, m.client_message_id, m.reply_to_message_id, m.resource_id, m.edited_at, m.created_at, u.user_uuid sender_user_uuid, u.nickname sender_nickname " +
                            "FROM chat_messages m JOIN users u ON u.id = m.sender_user_id " +
                            "WHERE m.conversation_id = ? " +
                            "ORDER BY m.id DESC LIMIT ?",
                    conv.get("id"), size
            );
        } else {
            rows = jdbcTemplate.queryForList(
                    "SELECT m.id internal_id, m.message_uuid, m.message_type, m.content_text, m.content_json, m.status, m.client_message_id, m.reply_to_message_id, m.resource_id, m.edited_at, m.created_at, u.user_uuid sender_user_uuid, u.nickname sender_nickname " +
                            "FROM chat_messages m JOIN users u ON u.id = m.sender_user_id " +
                            "WHERE m.conversation_id = ? AND m.id < ? " +
                            "ORDER BY m.id DESC LIMIT ?",
                    conv.get("id"), cursorId, size
            );
        }

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            items.add(buildMessage(row));
        }
        String nextCursor = null;
        if (!rows.isEmpty()) {
            Long minInternal = asLong(rows.get(rows.size() - 1).get("internal_id"));
            nextCursor = minInternal == null ? null : String.valueOf(minInternal);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("items", items);
        data.put("nextCursor", nextCursor);
        writeMapCache(
                cacheKey,
                data,
                MESSAGE_LIST_CACHE_SECONDS,
                chatCacheIndexService.singleIndex(chatCacheIndexService.conversationUserIndexKey(user.getId(), str(conv.get("conversation_uuid"))))
        );
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> editMessage(String userUuid, String messageId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Long msgId = resolveMessageId(messageId, true);
        Map<String, Object> row = requireMessage(msgId);
        ensureMessageOwner(row, user.getId());

        String contentText = trim(str(request, "contentText"));
        if (contentText.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "contentText不能为空");
        }
        jdbcTemplate.update(
                "UPDATE chat_messages SET content_text = ?, edited_at = NOW(), updated_at = NOW() WHERE id = ?",
                contentText, msgId
        );
        evictConversationRelatedCache(asLong(row.get("conversation_id")));
        return messageByUuid(str(row.get("message_uuid")));
    }

    @Override
    @Transactional
    public Map<String, Object> recallMessage(String userUuid, String messageId) {
        User user = requireUser(userUuid);
        Long msgId = resolveMessageId(messageId, true);
        Map<String, Object> row = requireMessage(msgId);
        ensureMessageOwner(row, user.getId());
        jdbcTemplate.update(
                "UPDATE chat_messages SET status = 'recalled', deleted_at = NOW(), updated_at = NOW() WHERE id = ?",
                msgId
        );
        evictConversationRelatedCache(asLong(row.get("conversation_id")));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("messageId", str(row.get("message_uuid")));
        data.put("recalled", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> markRead(String userUuid, String messageId) {
        User user = requireUser(userUuid);
        Long msgId = resolveMessageId(messageId, true);
        Map<String, Object> msg = requireMessage(msgId);
        Long convId = asLong(msg.get("conversation_id"));
        ensureMembership(convId, user.getId());
        markReadInternal(convId, user.getId(), msgId);
        String conversationUuid = resolveConversationUuid(convId);
        refreshUnreadCounter(convId, conversationUuid, user.getId());
        evictConversationRelatedCache(convId);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("messageId", str(msg.get("message_uuid")));
        data.put("read", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> markReadBatch(String userUuid, Map<String, Object> request) {
        User user = requireUser(userUuid);
        String convUuid = trim(str(request, "conversationId"));
        Long convId = resolveConversationPk(convUuid);
        if (convId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "会话不存在");
        }
        ensureMembership(convId, user.getId());
        List<String> messageIds = strList(request.get("messageIds"));
        if (messageIds.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<String, Object>();
            empty.put("conversationId", convUuid);
            empty.put("readCount", 0);
            empty.put("async", false);
            return empty;
        }
        ChatReadBatchEvent event = new ChatReadBatchEvent();
        event.setUserId(user.getId());
        event.setConversationId(convId);
        event.setConversationUuid(convUuid);
        event.setMessageIds(messageIds);
        chatOutboxService.enqueueReadBatch(event);
        int affected = messageIds.size();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("conversationId", convUuid);
        data.put("readCount", affected);
        data.put("async", true);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> unreadCount(String userUuid, String conversationId) {
        User user = requireUser(userUuid);
        String cacheKey = unreadCacheKey(user.getId(), conversationId);
        Map<String, Object> cached = readMapCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        Long convId = asLong(conv.get("id"));
        String convUuid = str(conv.get("conversation_uuid"));
        Long unread = getUnreadCounter(convUuid, user.getId());
        if (unread == null) {
            unread = queryUnreadCount(convId, user.getId());
            setUnreadCounter(convUuid, user.getId(), unread);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("conversationId", conversationId);
        data.put("unreadCount", unread == null ? 0L : unread.longValue());
        writeMapCache(
                cacheKey,
                data,
                UNREAD_CACHE_SECONDS,
                chatCacheIndexService.singleIndex(chatCacheIndexService.conversationUserIndexKey(user.getId(), str(conv.get("conversation_uuid"))))
        );
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listPins(String userUuid, String conversationId) {
        User user = requireUser(userUuid);
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        String cacheKey = pinListCacheKey(user.getId(), str(conv.get("conversation_uuid")));
        Map<String, Object> cached = readMapCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.pinned_at, m.id internal_id, m.message_uuid, m.message_type, m.content_text, m.content_json, m.status, m.client_message_id, m.reply_to_message_id, m.resource_id, m.edited_at, m.created_at, u.user_uuid sender_user_uuid, u.nickname sender_nickname " +
                        "FROM chat_pinned_messages p " +
                        "JOIN chat_messages m ON m.id = p.message_id " +
                        "JOIN users u ON u.id = m.sender_user_id " +
                        "WHERE p.conversation_id = ? AND p.status = 'active' " +
                        "ORDER BY p.pinned_at DESC",
                conv.get("id")
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("pinnedAt", dt(row.get("pinned_at")));
            item.put("message", buildMessage(row));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("items", items);
        writeMapCache(
                cacheKey,
                data,
                PIN_LIST_CACHE_SECONDS,
                chatCacheIndexService.singleIndex(chatCacheIndexService.conversationUserIndexKey(user.getId(), str(conv.get("conversation_uuid"))))
        );
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> pinMessage(String userUuid, String conversationId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        ensureCanManageConversation(conv, user.getId());
        Long messagePk = resolveMessageId(trim(str(request, "messageId")), true);
        Map<String, Object> message = requireMessage(messagePk);
        if (asLong(conv.get("id")).longValue() != asLong(message.get("conversation_id")).longValue()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "消息不属于当前会话");
        }
        jdbcTemplate.update(
                "INSERT INTO chat_pinned_messages (conversation_id, message_id, pinned_by_user_id, pinned_at, status) VALUES (?, ?, ?, NOW(), 'active') " +
                        "ON DUPLICATE KEY UPDATE pinned_by_user_id = VALUES(pinned_by_user_id), pinned_at = NOW(), status = 'active', unpinned_at = NULL",
                conv.get("id"), messagePk, user.getId()
        );
        Map<String, Object> pinPayload = new LinkedHashMap<String, Object>();
        pinPayload.put("messageId", str(message.get("message_uuid")));
        logConversationEvent(asLong(conv.get("id")), user.getId(), "pin", pinPayload);
        evictConversationRelatedCache(asLong(conv.get("id")));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("conversationId", conversationId);
        data.put("messageId", str(message.get("message_uuid")));
        data.put("pinned", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> unpinMessage(String userUuid, String conversationId, String messageId) {
        User user = requireUser(userUuid);
        Map<String, Object> conv = requireConversationMember(conversationId, user.getId());
        ensureCanManageConversation(conv, user.getId());
        Long messagePk = resolveMessageId(messageId, true);
        jdbcTemplate.update(
                "UPDATE chat_pinned_messages SET status = 'inactive', unpinned_at = NOW() WHERE conversation_id = ? AND message_id = ? AND status = 'active'",
                conv.get("id"), messagePk
        );
        Map<String, Object> unpinPayload = new LinkedHashMap<String, Object>();
        unpinPayload.put("messageId", messageId);
        logConversationEvent(asLong(conv.get("id")), user.getId(), "unpin", unpinPayload);
        evictConversationRelatedCache(asLong(conv.get("id")));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("conversationId", conversationId);
        data.put("messageId", messageId);
        data.put("pinned", false);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> wsToken(String userUuid) {
        User user = requireUser(userUuid);
        String cacheKey = wsTokenCacheKey(user.getId());
        if (redisTemplate != null) {
            try {
                String token = redisTemplate.opsForValue().get(cacheKey);
                if (token != null && !token.trim().isEmpty()) {
                    Long ttl = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
                    long expiresIn = ttl == null || ttl.longValue() <= 0L ? WS_TOKEN_CACHE_SECONDS : ttl.longValue();
                    Map<String, Object> cachedData = new LinkedHashMap<String, Object>();
                    cachedData.put("token", token);
                    cachedData.put("expiresIn", expiresIn);
                    return cachedData;
                }
            } catch (Exception ignore) {
            }
        }
        String token = UUID.randomUUID().toString().replace("-", "") + "." + System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("token", token);
        data.put("expiresIn", WS_TOKEN_CACHE_SECONDS);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, token, WS_TOKEN_CACHE_SECONDS, TimeUnit.SECONDS);
            } catch (Exception ignore) {
            }
        }
        return data;
    }

    private void addOrReactivateMember(Long conversationId, Long userId, String role) {
        jdbcTemplate.update(
                "INSERT INTO chat_conversation_members (conversation_id, user_id, member_role, join_source, status, joined_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'manual', 'active', NOW(), NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE status = 'active', member_role = VALUES(member_role), joined_at = NOW(), left_at = NULL, updated_at = NOW()",
                conversationId, userId, role
        );
    }

    private Map<String, Object> requireConversationMember(String conversationUuid, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT c.id, c.conversation_uuid, c.space_id, c.conversation_type, c.name, c.avatar_url, c.status, c.owner_user_id, owner.user_uuid owner_user_uuid, c.last_message_at, " +
                        "m.member_role, m.is_muted, m.mute_until, m.last_read_message_id " +
                        "FROM chat_conversations c " +
                        "JOIN chat_conversation_members m ON m.conversation_id = c.id " +
                        "LEFT JOIN users owner ON owner.id = c.owner_user_id " +
                        "WHERE c.conversation_uuid = ? AND m.user_id = ? AND m.status = 'active'",
                conversationUuid, userId
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "会话不存在或无权限访问");
        }
        return rows.get(0);
    }

    private void ensureCanManageConversation(Map<String, Object> conversationRow, Long userId) {
        String role = str(conversationRow.get("member_role")).toLowerCase(Locale.ROOT);
        Long ownerId = asLong(conversationRow.get("owner_user_id"));
        if ("owner".equals(role) || "admin".equals(role)) {
            return;
        }
        if (ownerId != null && ownerId.longValue() == userId.longValue()) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, 403, "无权限执行该操作");
    }

    private User requireUser(String userUuid) {
        User user = userMapper.findByUserUuid(userUuid);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "用户未登录或不存在");
        }
        return user;
    }

    private Map<String, Object> requireMessage(Long messageId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, message_uuid, conversation_id, sender_user_id, message_type, content_text, content_json, status, client_message_id, reply_to_message_id, resource_id, edited_at, created_at " +
                        "FROM chat_messages WHERE id = ?",
                messageId
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "消息不存在");
        }
        return rows.get(0);
    }

    private void ensureMessageOwner(Map<String, Object> messageRow, Long userId) {
        Long sender = asLong(messageRow.get("sender_user_id"));
        if (sender == null || sender.longValue() != userId.longValue()) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "仅发送者可操作消息");
        }
    }

    private void ensureMembership(Long conversationId, Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM chat_conversation_members WHERE conversation_id = ? AND user_id = ? AND status = 'active'",
                Long.class,
                conversationId, userId
        );
        if (count == null || count.longValue() <= 0L) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "无会话访问权限");
        }
    }

    private void markReadInternal(Long convId, Long userId, Long msgId) {
        jdbcTemplate.update(
                "INSERT INTO chat_message_receipts (message_id, user_id, receipt_type, receipt_at, created_at) VALUES (?, ?, 'read', NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE receipt_at = VALUES(receipt_at)",
                msgId, userId
        );
        jdbcTemplate.update(
                "UPDATE chat_conversation_members SET last_read_message_id = ?, last_read_at = NOW(), updated_at = NOW() " +
                        "WHERE conversation_id = ? AND user_id = ?",
                msgId, convId, userId
        );
    }

    private Long resolveConversationPk(String conversationUuid) {
        if (conversationUuid == null || conversationUuid.trim().isEmpty()) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM chat_conversations WHERE conversation_uuid = ?",
                (rs, rowNum) -> rs.getLong(1),
                conversationUuid
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long resolveMessageId(String messageIdOrUuid, boolean required) {
        String value = trim(messageIdOrUuid);
        if (value.isEmpty()) {
            if (required) {
                throw new BizException(ErrorCode.INVALID_PARAM, 400, "messageId不能为空");
            }
            return null;
        }
        if (isDigits(value)) {
            return Long.valueOf(value);
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM chat_messages WHERE message_uuid = ?",
                (rs, rowNum) -> rs.getLong(1),
                value
        );
        if (ids.isEmpty()) {
            if (required) {
                throw new BizException(ErrorCode.NOT_FOUND, 404, "消息不存在");
            }
            return null;
        }
        return ids.get(0);
    }

    private Map<String, Object> messageByUuid(String messageUuid) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT m.id internal_id, m.message_uuid, m.message_type, m.content_text, m.content_json, m.status, m.client_message_id, m.reply_to_message_id, m.resource_id, m.edited_at, m.created_at, u.user_uuid sender_user_uuid, u.nickname sender_nickname " +
                        "FROM chat_messages m JOIN users u ON u.id = m.sender_user_id WHERE m.message_uuid = ?",
                messageUuid
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "消息不存在");
        }
        return buildMessage(rows.get(0));
    }

    private Map<String, Object> buildMessage(Map<String, Object> row) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", str(row.get("message_uuid")));
        data.put("messageType", str(row.get("message_type")));
        data.put("contentText", str(row.get("content_text")));
        data.put("content", parseJson(str(row.get("content_json"))));
        data.put("status", str(row.get("status")));
        data.put("clientMessageId", str(row.get("client_message_id")));
        data.put("replyToMessageId", row.get("reply_to_message_id"));
        data.put("resourceId", row.get("resource_id"));
        data.put("editedAt", dt(row.get("edited_at")));
        data.put("createdAt", dt(row.get("created_at")));
        Map<String, Object> sender = new LinkedHashMap<String, Object>();
        sender.put("userId", str(row.get("sender_user_uuid")));
        sender.put("nickname", str(row.get("sender_nickname")));
        data.put("sender", sender);
        return data;
    }

    private Map<String, Object> buildLastMessageSummary(Map<String, Object> row) {
        String messageId = str(row.get("last_message_uuid"));
        if (messageId.isEmpty()) {
            return null;
        }
        Map<String, Object> lastMessage = new LinkedHashMap<String, Object>();
        lastMessage.put("id", messageId);
        lastMessage.put("messageType", str(row.get("last_message_type")));
        lastMessage.put("contentText", str(row.get("last_message_content_text")));
        lastMessage.put("createdAt", dt(row.get("last_message_created_at")));
        return lastMessage;
    }

    private Long resolveSpaceId(Object value) {
        String spaceIdOrUuid = trim(str(value));
        if (spaceIdOrUuid.isEmpty()) {
            return null;
        }
        if (isDigits(spaceIdOrUuid)) {
            return Long.valueOf(spaceIdOrUuid);
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM spaces WHERE space_uuid = ?",
                (rs, rowNum) -> rs.getLong(1),
                spaceIdOrUuid
        );
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "spaceId不存在");
        }
        return ids.get(0);
    }

    private void logConversationEvent(Long conversationId, Long operatorUserId, String eventType, Object payload) {
        if (conversationId == null || operatorUserId == null || trim(eventType).isEmpty()) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO chat_conversation_events (event_uuid, conversation_id, operator_user_id, event_type, event_payload_json, created_at) " +
                        "VALUES (?, ?, ?, ?, CAST(? AS JSON), NOW())",
                UUID.randomUUID().toString(), conversationId, operatorUserId, eventType, jsonString(payload)
        );
    }

    private String conversationListCacheKey(Long userId, String normalizedKeyword, int page, int pageSize) {
        String keywordPart = normalizedKeyword == null ? "_" : normalizedKeyword;
        return "chat:conv:list:user:" + userId + ":kw:" + keywordPart + ":p:" + page + ":ps:" + pageSize;
    }

    private String conversationDetailCacheKey(Long userId, String conversationId) {
        return "chat:conv:detail:user:" + userId + ":conv:" + trim(conversationId);
    }

    private String unreadCacheKey(Long userId, String conversationId) {
        return "chat:conv:unread:user:" + userId + ":conv:" + trim(conversationId);
    }

    private String unreadCounterKey(Long userId, String conversationId) {
        return "chat:conv:unread:counter:user:" + userId + ":conv:" + trim(conversationId);
    }

    private String messageListCacheKey(Long userId, String conversationId, Long cursor, int size) {
        String cursorPart = cursor == null ? "_" : String.valueOf(cursor);
        return "chat:msg:list:user:" + userId + ":conv:" + trim(conversationId) + ":cursor:" + cursorPart + ":limit:" + size;
    }

    private String memberListCacheKey(Long userId, String conversationId, int page, int pageSize) {
        return "chat:member:list:user:" + userId + ":conv:" + trim(conversationId) + ":p:" + page + ":ps:" + pageSize;
    }

    private String pinListCacheKey(Long userId, String conversationId) {
        return "chat:pin:list:user:" + userId + ":conv:" + trim(conversationId);
    }

    private String wsTokenCacheKey(Long userId) {
        return "chat:ws:token:user:" + userId;
    }

    private String messageDedupKey(String conversationId, Long userId, String clientMessageId) {
        return "chat:msg:dedup:conv:" + trim(conversationId) + ":user:" + userId + ":client:" + trim(clientMessageId);
    }

    private Map<String, Object> readMapCache(String key) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached == null || cached.trim().isEmpty()) {
                return null;
            }
            return objectMapper.readValue(cached, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeMapCache(String key, Map<String, Object> data, long ttlSeconds, List<String> indexKeys) {
        if (redisTemplate == null || data == null || ttlSeconds <= 0L) {
            return;
        }
        try {
            String text = objectMapper.writeValueAsString(data);
            chatCacheIndexService.putCacheValue(key, text, ttlSeconds, indexKeys);
        } catch (Exception ignore) {
        }
    }

    private String readMessageDedup(String conversationId, Long userId, String clientMessageId) {
        if (redisTemplate == null || userId == null || trim(clientMessageId).isEmpty()) {
            return "";
        }
        try {
            String v = redisTemplate.opsForValue().get(messageDedupKey(conversationId, userId, clientMessageId));
            return v == null ? "" : v;
        } catch (Exception ex) {
            return "";
        }
    }

    private void writeMessageDedup(String conversationId, Long userId, String clientMessageId, String messageUuid) {
        if (redisTemplate == null || userId == null || trim(clientMessageId).isEmpty() || trim(messageUuid).isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    messageDedupKey(conversationId, userId, clientMessageId),
                    messageUuid,
                    MESSAGE_DEDUP_CACHE_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception ignore) {
        }
    }

    private Long getUnreadCounter(String conversationUuid, Long userId) {
        if (redisTemplate == null || userId == null) {
            return null;
        }
        try {
            String text = redisTemplate.opsForValue().get(unreadCounterKey(userId, conversationUuid));
            if (text == null || text.trim().isEmpty() || !isDigits(text.trim())) {
                return null;
            }
            return Long.valueOf(text.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private void setUnreadCounter(String conversationUuid, Long userId, Long value) {
        if (redisTemplate == null || userId == null || value == null) {
            return;
        }
        try {
            long safe = value.longValue() < 0L ? 0L : value.longValue();
            redisTemplate.opsForValue().set(
                    unreadCounterKey(userId, conversationUuid),
                    String.valueOf(safe),
                    UNREAD_COUNTER_CACHE_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception ignore) {
        }
    }

    private void deleteUnreadCounter(String conversationUuid, Long userId) {
        if (redisTemplate == null || userId == null) {
            return;
        }
        try {
            redisTemplate.delete(unreadCounterKey(userId, conversationUuid));
        } catch (Exception ignore) {
        }
    }

    private Long queryUnreadCount(Long conversationId, Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(1) " +
                        "FROM chat_messages m " +
                        "WHERE m.conversation_id = ? " +
                        "AND m.id > IFNULL((SELECT cm.last_read_message_id FROM chat_conversation_members cm WHERE cm.conversation_id = ? AND cm.user_id = ?), 0)",
                Long.class,
                conversationId, conversationId, userId
        );
    }

    private String resolveConversationUuid(Long conversationId) {
        if (conversationId == null) {
            return "";
        }
        String uuid = jdbcTemplate.queryForObject(
                "SELECT conversation_uuid FROM chat_conversations WHERE id = ?",
                String.class,
                conversationId
        );
        return uuid == null ? "" : uuid;
    }

    private void refreshUnreadCounter(Long conversationId, String conversationUuid, Long userId) {
        if (conversationId == null || userId == null) {
            return;
        }
        Long unread = queryUnreadCount(conversationId, userId);
        setUnreadCounter(conversationUuid, userId, unread == null ? 0L : unread);
    }

    private void syncUnreadCounterForAllMembers(Long conversationId, String conversationUuid) {
        if (conversationId == null || conversationUuid == null || conversationUuid.trim().isEmpty()) {
            return;
        }
        List<Long> memberUserIds = jdbcTemplate.query(
                "SELECT user_id FROM chat_conversation_members WHERE conversation_id = ? AND status = 'active'",
                (rs, rowNum) -> rs.getLong(1),
                conversationId
        );
        for (Long uid : memberUserIds) {
            refreshUnreadCounter(conversationId, conversationUuid, uid);
        }
    }

    private void incrementUnreadCounterForConversation(Long conversationId, String conversationUuid, Long senderUserId) {
        if (redisTemplate == null || conversationId == null || conversationUuid == null || conversationUuid.trim().isEmpty()) {
            return;
        }
        try {
            List<Long> memberUserIds = jdbcTemplate.query(
                    "SELECT user_id FROM chat_conversation_members WHERE conversation_id = ? AND status = 'active'",
                    (rs, rowNum) -> rs.getLong(1),
                    conversationId
            );
            for (Long uid : memberUserIds) {
                if (uid == null) {
                    continue;
                }
                if (senderUserId != null && uid.longValue() == senderUserId.longValue()) {
                    setUnreadCounter(conversationUuid, uid, 0L);
                    continue;
                }
                String key = unreadCounterKey(uid, conversationUuid);
                redisTemplate.opsForValue().increment(key, 1L);
                redisTemplate.expire(key, UNREAD_COUNTER_CACHE_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception ignore) {
        }
    }

    private void evictConversationRelatedCache(Long conversationId) {
        if (redisTemplate == null || conversationId == null) {
            return;
        }
        try {
            List<Long> memberUserIds = jdbcTemplate.query(
                    "SELECT user_id FROM chat_conversation_members WHERE conversation_id = ? AND status = 'active'",
                    (rs, rowNum) -> rs.getLong(1),
                    conversationId
            );
            String conversationUuid = jdbcTemplate.queryForObject(
                    "SELECT conversation_uuid FROM chat_conversations WHERE id = ?",
                    String.class,
                    conversationId
            );
            if (memberUserIds == null || memberUserIds.isEmpty()) {
                return;
            }
            for (Long uid : memberUserIds) {
                if (uid == null) {
                    continue;
                }
                chatCacheIndexService.evictByIndexes(Arrays.asList(
                        chatCacheIndexService.userListIndexKey(uid),
                        chatCacheIndexService.conversationUserIndexKey(uid, conversationUuid)
                ));
            }
        } catch (Exception ignore) {
        }
    }

    private String jsonString(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            if (value instanceof String) {
                String text = trim((String) value);
                return text.isEmpty() ? "{}" : text;
            }
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "content格式不正确");
        }
    }

    private Object parseJson(String text) {
        String json = trim(text);
        if (json.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            return json;
        }
    }

    private LocalDateTime parseOptionalDateTime(String text) {
        String s = trim(text);
        if (s.isEmpty()) {
            return null;
        }
        try {
            if (s.contains("T") && (s.endsWith("Z") || s.contains("+"))) {
                return OffsetDateTime.parse(s).toLocalDateTime();
            }
            if (s.contains("T")) {
                return LocalDateTime.parse(s);
            }
            return LocalDateTime.parse(s, FMT);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "muteUntil时间格式不正确");
        }
    }

    private String normalizeConversationType(String type) {
        String t = trim(type).toLowerCase(Locale.ROOT);
        if ("group".equals(t) || "direct".equals(t)) {
            return t;
        }
        throw new BizException(ErrorCode.INVALID_PARAM, 400, "conversationType仅支持group/direct");
    }

    private String normalizeMessageType(String type) {
        String t = trim(type).toLowerCase(Locale.ROOT);
        if ("text".equals(t) || "image".equals(t) || "file".equals(t) || "system".equals(t)) {
            return t;
        }
        throw new BizException(ErrorCode.INVALID_PARAM, 400, "messageType不合法");
    }

    private int[] normalizePage(Integer page, Integer pageSize, int maxPageSize) {
        int p = page == null || page.intValue() < 1 ? 1 : page.intValue();
        int s = pageSize == null || pageSize.intValue() < 1 ? 20 : pageSize.intValue();
        if (s > maxPageSize) {
            s = maxPageSize;
        }
        return new int[]{p, s};
    }

    private String normalizeLike(String text) {
        String s = trim(text);
        if (s.isEmpty()) {
            return null;
        }
        return "%" + s + "%";
    }

    private List<String> strList(Object value) {
        List<String> out = new ArrayList<String>();
        if (!(value instanceof List)) {
            return out;
        }
        List<?> raw = (List<?>) value;
        for (Object o : raw) {
            String s = trim(str(o));
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private String joinClauses(List<String> clauses) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clauses.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(clauses.get(i));
        }
        return sb.toString();
    }

    private String dt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return FMT.format((LocalDateTime) value);
        }
        if (value instanceof Timestamp) {
            return FMT.format(((Timestamp) value).toLocalDateTime());
        }
        return str(value);
    }

    private boolean boolInt(Object value) {
        if (value == null) {
            return false;
        }
        return asLong(value) != null && asLong(value).longValue() != 0L;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        String s = trim(str(value));
        if (s.isEmpty() || !isDigits(s)) {
            return null;
        }
        return Long.valueOf(s);
    }

    private Long longVal(Object value) {
        Long n = asLong(value);
        return n == null ? 0L : n;
    }

    private boolean isDigits(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String str(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return "";
        }
        return str(map.get(key));
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String emptyToNull(String s) {
        return trim(s).isEmpty() ? null : trim(s);
    }
}

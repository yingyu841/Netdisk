package com.netdisk.service.impl;

import com.netdisk.mapper.AiAgentConversationMapper;
import com.netdisk.mapper.AiConversationMapper;
import com.netdisk.pojo.entity.AiAgentConversation;
import com.netdisk.pojo.entity.AiConversation;
import com.netdisk.pojo.vo.AiConversationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AiConversationService {

    private final AiConversationMapper conversationMapper;
    private final AiAgentConversationMapper messageMapper;

    public AiConversationService(AiConversationMapper conversationMapper, AiAgentConversationMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    /**
     * 创建或获取会话
     */
    @Transactional
    public AiConversation getOrCreateConversation(String userUuid, String conversationId) {
        AiConversation conversation = conversationMapper.findByConversationId(conversationId);
        if (conversation == null) {
            conversation = new AiConversation();
            conversation.setUserUuid(userUuid);
            conversation.setConversationId(conversationId);
            conversation.setTitle("新对话");
            conversationMapper.insert(conversation);
            log.info("创建新会话: conversationId={}, userUuid={}", conversationId, userUuid);
        }
        return conversation;
    }

    /**
     * 保存用户消息
     */
    @Transactional
    public void saveUserMessage(String userUuid, String conversationId, String content) {
        AiAgentConversation msg = new AiAgentConversation();
        msg.setUserUuid(userUuid);
        msg.setConversationId(conversationId);
        msg.setRole("user");
        msg.setContent(content);
        messageMapper.insert(msg);
    }

    /**
     * 保存 AI 消息
     */
    @Transactional
    public void saveAiMessage(String userUuid, String conversationId, String content, String toolName) {
        AiAgentConversation msg = new AiAgentConversation();
        msg.setUserUuid(userUuid);
        msg.setConversationId(conversationId);
        msg.setRole("ai");
        msg.setContent(content);
        msg.setToolName(toolName);
        messageMapper.insert(msg);
    }

    /**
     * 更新会话标题（根据首条用户消息生成）
     */
    @Transactional
    public void updateConversationTitle(String conversationId, String title) {
        if (title != null && title.length() > 50) {
            title = title.substring(0, 50) + "...";
        }
        conversationMapper.updateTitle(conversationId, title);
    }

    /**
     * 获取用户的会话列表
     */
    public List<AiConversationVO> listConversations(String userUuid) {
        List<AiConversation> conversations = conversationMapper.findByUserUuid(userUuid);
        return conversations.stream().map(conv -> {
            AiConversationVO vo = new AiConversationVO();
            vo.setConversationId(conv.getConversationId());
            vo.setLastMessageAt(conv.getUpdatedAt());
            Long count = messageMapper.countByConversationId(conv.getConversationId());
            vo.setMessageCount(count != null ? count.intValue() : 0);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 获取会话详情（含消息）
     */
    public AiConversationVO getConversation(String userUuid, String conversationId) {
        AiConversation conversation = conversationMapper.findByConversationId(conversationId);
        if (conversation == null || !conversation.getUserUuid().equals(userUuid)) {
            return null;
        }

        AiConversationVO vo = new AiConversationVO();
        vo.setConversationId(conversation.getConversationId());
        vo.setLastMessageAt(conversation.getUpdatedAt());

        List<AiAgentConversation> messages = messageMapper.findByUserAndConversation(userUuid, conversationId);
        vo.setMessageCount(messages.size());

        List<AiConversationVO.AiMessageVO> messageVOs = messages.stream().map(msg -> {
            AiConversationVO.AiMessageVO m = new AiConversationVO.AiMessageVO();
            m.setRole(msg.getRole());
            m.setContent(msg.getContent());
            m.setToolName(msg.getToolName());
            m.setCreatedAt(msg.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        vo.setMessages(messageVOs);

        return vo;
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteConversation(String conversationId) {
        messageMapper.deleteByConversationId(conversationId);
        conversationMapper.deleteByConversationId(conversationId);
        log.info("删除会话: conversationId={}", conversationId);
    }
}

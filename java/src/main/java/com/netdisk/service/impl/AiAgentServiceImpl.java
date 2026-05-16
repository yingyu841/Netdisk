package com.netdisk.service.impl;

import com.netdisk.ai.tools.NetdiskAssistantTools;
import com.netdisk.common.exception.BizException;
import com.netdisk.config.LangChain4jConfig;
import com.netdisk.pojo.dto.AiAgentConfigDTO;
import com.netdisk.pojo.dto.AiChatRequestDTO;
import com.netdisk.pojo.vo.AiChatResponseVO;
import com.netdisk.pojo.vo.AiConversationVO;
import com.netdisk.service.AiAgentService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Service
@Slf4j
public class AiAgentServiceImpl implements AiAgentService {

    private final LangChain4jConfig config;
    private final NetdiskAssistantTools netdiskTools;
    private final AiConversationService conversationService;

    private final ConcurrentHashMap<String, ChatMemory> memoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> memoryAccessTime = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    private static final long MEMORY_CLEANUP_INTERVAL_MINUTES = 10;
    private static final long MEMORY_EXPIRE_MINUTES = 30;

    // 单例 Model（双重检查锁定）
    private volatile ChatLanguageModel cachedModel;

    public AiAgentServiceImpl(LangChain4jConfig config, NetdiskAssistantTools netdiskTools,
            AiConversationService conversationService) {
        this.config = config;
        this.netdiskTools = netdiskTools;
        this.conversationService = conversationService;
    }

    @PostConstruct
    public void init() {
        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredMemory,
                MEMORY_CLEANUP_INTERVAL_MINUTES,
                MEMORY_CLEANUP_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
        log.info("AI Agent 初始化完成，Memory 缓存定时清理已启动");
    }

    @PreDestroy
    public void destroy() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
        }
    }

    @Override
    public AiChatResponseVO chat(String userUuid, AiChatRequestDTO request) {
        if (!config.isEnabled()) {
            throw new BizException(503, 503, "AI 智能体功能未启用");
        }
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new BizException(400, 400, "消息内容不能为空");
        }

        netdiskTools.setCurrentUser(userUuid);
        try {
            String conversationId = request.getConversationId();
            boolean isNewConversation = false;
            if (conversationId == null || conversationId.isEmpty()) {
                conversationId = UUID.randomUUID().toString();
                isNewConversation = true;
            }

            // 确保会话已持久化
            conversationService.getOrCreateConversation(userUuid, conversationId);

            ChatMemory chatMemory = memoryCache.computeIfAbsent(conversationId, k -> {
                ChatMemory mem = MessageWindowChatMemory.builder()
                        .maxMessages(config.getMaxMemoryMessages())
                        .build();
                if (config.getSystemPrompt() != null) {
                    mem.add(new SystemMessage(config.getSystemPrompt()));
                }
                return mem;
            });
            memoryAccessTime.put(conversationId, LocalDateTime.now());

            // 保存用户消息
            conversationService.saveUserMessage(userUuid, conversationId, request.getMessage());

            String finalResponse = agentChat(chatMemory, request.getMessage());

            // 保存 AI 消息
            conversationService.saveAiMessage(userUuid, conversationId, finalResponse, null);

            // 新会话生成标题
            if (isNewConversation && finalResponse != null && !finalResponse.isEmpty()) {
                String title = request.getMessage().length() > 20
                        ? request.getMessage().substring(0, 20) + "..."
                        : request.getMessage();
                conversationService.updateConversationTitle(conversationId, title);
            }

            AiChatResponseVO vo = new AiChatResponseVO();
            vo.setConversationId(conversationId);
            vo.setReply(finalResponse);
            vo.setRepliedAt(LocalDateTime.now());
            return vo;
        } finally {
            netdiskTools.clearCurrentUser();
        }
    }

    /**
     * 使用 AiServices 创建 Agent，自动处理工具调用
     * AiServices 会自动将 userMessage 和 AI 响应添加到 chatMemory
     */
    private String agentChat(ChatMemory chatMemory, String userMessage) {
        ChatLanguageModel model = getOrCreateModel();

        Agent agent = AiServices.builder(Agent.class)
                .chatLanguageModel(model)
                .chatMemory(chatMemory)
                .tools(netdiskTools)
                .build();

        String response = agent.chat(userMessage);
        log.info("Agent 最终响应: {}", response);
        return response;
    }

    /**
     * AiServices 需要接口方法有参数来定义 user message
     */
    public interface Agent {
        String chat(String userMessage);
    }

    /**
     * 获取或创建 Model 实例（线程安全单例）
     */
    private ChatLanguageModel getOrCreateModel() {
        if (cachedModel == null) {
            synchronized (this) {
                if (cachedModel == null) {
                    if (!config.isEnabled() || config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
                        throw new BizException(503, 503, "AI 模型未配置");
                    }
                    cachedModel = OpenAiChatModel.builder()
                            .apiKey(config.getApiKey())
                            .baseUrl(config.getBaseUrl())
                            .modelName(config.getModelName())
                            .temperature(config.getTemperature())
                            .maxTokens(config.getMaxTokens())
                            .build();
                    log.info("AI Model 实例已创建: model={}, baseUrl={}", config.getModelName(), config.getBaseUrl());
                }
            }
        }
        return cachedModel;
    }

    /**
     * 清理过期的 Memory 缓存
     */
    private void cleanupExpiredMemory() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int before = memoryCache.size();

            memoryAccessTime.entrySet().removeIf(entry -> {
                if (entry.getValue() == null)
                    return true;
                long minutes = ChronoUnit.MINUTES.between(entry.getValue(), now);
                return minutes > MEMORY_EXPIRE_MINUTES;
            });

            memoryCache.keySet().removeIf(key -> !memoryAccessTime.containsKey(key));

            int after = memoryCache.size();
            if (before != after) {
                log.info("Memory 缓存清理完成: 清理前 {} 个, 清理后 {} 个", before, after);
            }
        } catch (Exception e) {
            log.error("Memory 缓存清理异常", e);
        }
    }

    // ============ 以下为接口实现 ============

    @Override
    public List<AiConversationVO> listConversations(String userUuid) {
        return conversationService.listConversations(userUuid);
    }

    @Override
    public AiConversationVO getConversation(String userUuid, String conversationId) {
        return conversationService.getConversation(userUuid, conversationId);
    }

    @Override
    public void deleteConversation(String conversationId) {
        memoryCache.remove(conversationId);
        memoryAccessTime.remove(conversationId);
        conversationService.deleteConversation(conversationId);
    }

    @Override
    public AiAgentConfigDTO getConfig(String userUuid) {
        AiAgentConfigDTO dto = new AiAgentConfigDTO();
        dto.setModelName(config.getModelName());
        return dto;
    }

    @Override
    public void updateConfig(String userUuid, AiAgentConfigDTO dto) {
        // TODO: 保存配置到数据库
    }
}

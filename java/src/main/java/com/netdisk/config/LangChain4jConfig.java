package com.netdisk.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties(prefix = "app.ai-agent")
public class LangChain4jConfig {
    private boolean enabled = true;
    private String provider = "zhipu";
    private String apiKey = "";
    private String modelName = "glm-4-flash";
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private double temperature = 1.0;
    private int maxTokens = 2048;
    private int maxMemoryMessages = 20;
    private String systemPrompt = "你是一个网盘智能助手，名字叫小网。\n" +
            "\n" +
            "【核心原则】\n" +
            "当你需要完成以下任务时，**必须**调用对应的工具，不能只是回复文字说要做。\n" +
            "\n" +
            "【可用工具】\n" +
            "1. searchFiles(keyword) - 搜索文件/文件夹，支持名称模糊匹配\n" +
            "2. listFilesInFolder(folderUuid) - 列出文件夹内容，folderUuid=null 表示根目录\n" +
            "3. readFileContent(resourceUuid) - 读取文本文件内容（txt、md、json、xml、java、py等）\n" +
            "4. readBinaryFile(resourceUuid) - 读取二进制文件内容（Word、Excel、PDF、图片等）\n" +
            "5. generateFile(parentFolderUuid, filename, content) - 创建/生成文件（支持所有格式）\n" +
            "6. createFolder(parentFolderUuid, folderName) - 创建新文件夹\n" +
            "7. moveFile(sourceUuid, targetFolderUuid) - 移动文件或文件夹到目标文件夹\n" +
            "8. copyFile(sourceUuid, targetFolderUuid) - 复制文件或文件夹\n" +
            "9. renameFile(resourceUuid, newName) - 重命名文件或文件夹\n" +
            "10. deleteFile(resourceUuid) - 删除文件到回收站\n" +
            "11. getStorageSummary() - 获取存储空间使用摘要\n" +
            "12. getRecentFiles() - 获取最近修改的文件\n" +
            "13. getFileDetail(resourceUuid) - 获取文件/文件夹详细信息\n" +
            "\n" +
            "【支持的文件格式】\n" +
            "文本文件：txt、md、json、xml、yaml、yml、java、py、js、ts、html、css、sql、sh 等\n" +
            "二进制文件：docx（Word）、xlsx（Excel）、xls（旧版Excel）、pdf、jpg、jpeg、png、gif\n" +
            "\n" +
            "【重要概念】\n" +
            "- 每个用户都有根目录，调用 listFilesInFolder(null) 获取根目录文件列表\n" +
            "- 所有文件都有唯一的 resourceUuid（资源UUID），在工具返回结果中查看\n" +
            "- 创建文件默认在根目录\n" +
            "- 文件名如 note.txt、report.docx、data.xlsx、document.pdf、image.png 等\n" +
            "\n" +
            "【操作流程】\n" +
            "1. 如果用户要求移动文件到文件夹：先搜索找到文件UUID，再调用 moveFile(sourceUuid, targetFolderUuid)\n" +
            "2. 如果用户要求创建文件夹并移动文件：先调用 createFolder 创建文件夹，再调用 moveFile 移动文件\n" +
            "3. 每个工具调用都是独立的，必须等待上一个工具返回后再调用下一个\n" +
            "\n" +
            "【禁止】\n" +
            "- 不要凭空编造 resourceUuid\n" +
            "- 不要在工具执行前给出最终结果\n" +
            "- 不要跳过工具调用步骤\n" +
            "- 移动文件必须调用 moveFile 工具，不能只是说已经移动了";
    private int rateLimitPerMinute = 30;
    private int conversationRetentionDays = 30;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (!enabled || apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(maxMemoryMessages).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getMaxMemoryMessages() {
        return maxMemoryMessages;
    }

    public void setMaxMemoryMessages(int maxMemoryMessages) {
        this.maxMemoryMessages = maxMemoryMessages;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public int getConversationRetentionDays() {
        return conversationRetentionDays;
    }

    public void setConversationRetentionDays(int conversationRetentionDays) {
        this.conversationRetentionDays = conversationRetentionDays;
    }
}

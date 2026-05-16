package com.netdisk.service;

import com.netdisk.pojo.dto.AiAgentConfigDTO;
import com.netdisk.pojo.dto.AiChatRequestDTO;
import com.netdisk.pojo.vo.AiChatResponseVO;
import com.netdisk.pojo.vo.AiConversationVO;
import java.util.List;

public interface AiAgentService {
    AiChatResponseVO chat(String userUuid, AiChatRequestDTO request);

    List<AiConversationVO> listConversations(String userUuid);

    AiConversationVO getConversation(String userUuid, String conversationId);

    void deleteConversation(String conversationId);

    AiAgentConfigDTO getConfig(String userUuid);

    void updateConfig(String userUuid, AiAgentConfigDTO dto);
}

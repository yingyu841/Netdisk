package com.netdisk.mapper;
import com.netdisk.pojo.entity.AiAgentConversation;
import org.apache.ibatis.annotations.Param;
import java.util.List;
public interface AiAgentConversationMapper {
    int insert(AiAgentConversation record);
    List<AiAgentConversation> findByUserAndConversation(@Param("userUuid") String userUuid, @Param("conversationId") String conversationId);
    List<AiAgentConversation> findByUserUuid(@Param("userUuid") String userUuid);
    int deleteByConversationId(@Param("conversationId") String conversationId);
    int deleteExpired(@Param("retentionDays") int retentionDays);
    Long countByConversationId(@Param("conversationId") String conversationId);
}

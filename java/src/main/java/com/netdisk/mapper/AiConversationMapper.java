package com.netdisk.mapper;

import com.netdisk.pojo.entity.AiConversation;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface AiConversationMapper {
    int insert(AiConversation record);

    AiConversation findByConversationId(@Param("conversationId") String conversationId);

    List<AiConversation> findByUserUuid(@Param("userUuid") String userUuid);

    int updateTitle(@Param("conversationId") String conversationId, @Param("title") String title);

    int deleteByConversationId(@Param("conversationId") String conversationId);

    int deleteExpired(@Param("retentionDays") int retentionDays);
}

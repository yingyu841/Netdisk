package com.netdisk.mapper;
import com.netdisk.pojo.entity.AiAgentConfig;
public interface AiAgentConfigMapper {
    int insert(AiAgentConfig record);
    AiAgentConfig findByUserUuid(String userUuid);
    int updateByUserUuid(AiAgentConfig record);
    int deleteByUserUuid(String userUuid);
}

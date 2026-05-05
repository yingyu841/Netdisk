package com.netdisk.mapper;

import com.netdisk.pojo.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 审计日志数据访问接口。
 */
@Mapper
public interface AuditLogMapper {
    /**
     * 统计用户操作记录总数。
     *
     * @param actorUserId 用户主键
     * @return 总数
     */
    Long countByActorUserId(@Param("actorUserId") Long actorUserId);

    /**
     * 分页查询用户操作记录。
     *
     * @param actorUserId 用户主键
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 操作记录
     */
    List<AuditLog> listByActorUserId(@Param("actorUserId") Long actorUserId,
                                     @Param("offset") Integer offset,
                                     @Param("pageSize") Integer pageSize);

    /**
     * 写入一条审计日志。
     */
    int insert(AuditLog entity);
}

package com.netdisk.mapper;

import com.netdisk.pojo.entity.Share;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分享链接数据访问。
 */
@Mapper
public interface ShareMapper {
    int insert(Share entity);

    Share findByShareUuid(@Param("shareUuid") String shareUuid);

    Long countByCreatorUserId(@Param("creatorUserId") Long creatorUserId);

    List<Share> listByCreatorUserId(
            @Param("creatorUserId") Long creatorUserId,
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize);

    int revokeByShareUuid(@Param("shareUuid") String shareUuid, @Param("creatorUserId") Long creatorUserId);

    int tryConsumeAccess(@Param("id") Long id);
}

package com.netdisk.mapper;

import com.netdisk.pojo.entity.Resource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分享资源关系数据访问。
 */
@Mapper
public interface ShareResourceMapper {
    int insertBatch(@Param("shareId") Long shareId, @Param("resourceIds") List<Long> resourceIds);

    Long countByShareId(@Param("shareId") Long shareId);

    List<Resource> listActiveResourcesByShareId(@Param("shareId") Long shareId);

    Resource findActiveResourceInShare(@Param("shareId") Long shareId, @Param("resourceUuid") String resourceUuid);
}

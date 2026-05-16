package com.netdisk.mapper;

import com.netdisk.pojo.entity.Space;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

/**
 * 空间数据访问接口。
 */
@Mapper
public interface SpaceMapper {
    /**
     * 按所有者查询个人空间。
     *
     * @param ownerUserId 用户主键
     * @return 个人空间
     */
    Space findPersonalByOwnerUserId(Long ownerUserId);

    /**
     * 新增空间。
     *
     * @param space 空间实体
     * @return 影响行数
     */
    int insert(Space space);

    /**
     * 查询用户个人空间使用情况。
     *
     * @param ownerUserId 用户主键
     * @return 包含 quota_bytes, used_bytes 的 Map
     */
    Map<String, Object> findPersonalSpaceUsage(@Param("ownerUserId") Long ownerUserId);
}

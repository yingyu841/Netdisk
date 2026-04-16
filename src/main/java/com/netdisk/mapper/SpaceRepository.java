package com.netdisk.mapper;

import com.netdisk.pojo.entity.SpaceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 空间数据访问接口。
 */
@Mapper
public interface SpaceRepository {
    /**
     * 按所有者查询个人空间。
     *
     * @param ownerUserId 用户主键
     * @return 个人空间
     */
    SpaceEntity findPersonalByOwnerUserId(Long ownerUserId);

    /**
     * 新增空间。
     *
     * @param space 空间实体
     * @return 影响行数
     */
    int insert(SpaceEntity space);
}

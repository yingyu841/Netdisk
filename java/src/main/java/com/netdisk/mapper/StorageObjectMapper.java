package com.netdisk.mapper;

import com.netdisk.pojo.entity.StorageObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 存储对象数据访问接口。
 */
@Mapper
public interface StorageObjectMapper {
    StorageObject findBySha256AndSize(@Param("sha256") String sha256, @Param("sizeBytes") Long sizeBytes);

    /**
     * 按主键查询存储对象。
     */
    StorageObject findById(@Param("id") Long id);

    int insert(StorageObject entity);

    int deleteById(@Param("id") Long id);
}

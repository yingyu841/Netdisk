package com.netdisk.mapper;

import com.netdisk.pojo.entity.StorageObjectEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 存储对象数据访问接口。
 */
@Mapper
public interface StorageObjectRepository {
    StorageObjectEntity findBySha256AndSize(@Param("sha256") String sha256, @Param("sizeBytes") Long sizeBytes);

    int insert(StorageObjectEntity entity);
}

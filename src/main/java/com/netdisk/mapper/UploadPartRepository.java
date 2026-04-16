package com.netdisk.mapper;

import com.netdisk.pojo.entity.UploadPartEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 上传分片数据访问接口。
 */
@Mapper
public interface UploadPartRepository {
    int upsert(UploadPartEntity entity);

    List<UploadPartEntity> listByUploadSessionId(Long uploadSessionId);

    Integer countByUploadSessionId(Long uploadSessionId);

    Long sumPartSizeByUploadSessionId(Long uploadSessionId);

    int deleteByUploadSessionId(Long uploadSessionId);

    UploadPartEntity findBySessionIdAndPartNumber(@Param("uploadSessionId") Long uploadSessionId, @Param("partNumber") Integer partNumber);
}

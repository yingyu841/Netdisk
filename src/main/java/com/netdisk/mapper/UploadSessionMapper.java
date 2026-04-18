package com.netdisk.mapper;

import com.netdisk.pojo.entity.UploadSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 上传会话数据访问接口。
 */
@Mapper
public interface UploadSessionMapper {
    int insert(UploadSession entity);

    UploadSession findByUploadUuid(String uploadUuid);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int complete(@Param("id") Long id, @Param("status") String status);
}

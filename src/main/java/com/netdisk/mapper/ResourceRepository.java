package com.netdisk.mapper;

import com.netdisk.pojo.entity.ResourceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资源目录数据访问接口。
 */
@Mapper
public interface ResourceRepository {
    /**
     * 查询用户根目录。
     *
     * @param userUuid 用户业务ID
     * @return 根目录
     */
    ResourceEntity findRootFolderByUserUuid(String userUuid);

    /**
     * 查询用户指定目录。
     *
     * @param userUuid 用户业务ID
     * @param resourceUuid 目录资源业务ID
     * @return 目录实体
     */
    ResourceEntity findFolderByResourceUuid(@Param("userUuid") String userUuid, @Param("resourceUuid") String resourceUuid);

    /**
     * 统计目录下资源数量。
     */
    Long countByParent(
            @Param("userUuid") String userUuid,
            @Param("parentId") Long parentId,
            @Param("keyword") String keyword);

    /**
     * 分页查询目录下资源。
     */
    List<ResourceEntity> listByParent(
            @Param("userUuid") String userUuid,
            @Param("parentId") Long parentId,
            @Param("keyword") String keyword,
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize);

    /**
     * 新增根目录。
     *
     * @param entity 根目录实体
     * @return 影响行数
     */
    int insertRootFolder(ResourceEntity entity);

    /**
     * 根据主键查询资源。
     *
     * @param id 主键
     * @return 资源实体
     */
    ResourceEntity findById(Long id);

    /**
     * 新增文件资源。
     *
     * @param entity 文件资源
     * @return 影响行数
     */
    int insertFileResource(ResourceEntity entity);

    /**
     * 统计父目录下同名资源数量（未删除）。
     *
     * @param parentId 父目录主键
     * @param nameNormalized 标准化名称
     * @return 数量
     */
    Integer countActiveByParentAndNameNormalized(@Param("parentId") Long parentId, @Param("nameNormalized") String nameNormalized);

    /**
     * 查询用户名下全部未删除的目录资源。
     *
     * @param userUuid 用户业务ID
     * @return 目录列表
     */
    List<ResourceEntity> listFoldersByUserUuid(String userUuid);
}

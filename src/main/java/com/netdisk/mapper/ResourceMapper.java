package com.netdisk.mapper;

import com.netdisk.pojo.entity.RecycleBinRow;
import com.netdisk.pojo.entity.RecycleEntry;
import com.netdisk.pojo.entity.Resource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资源目录数据访问接口。
 */
@Mapper
public interface ResourceMapper {
    /**
     * 查询用户根目录。
     *
     * @param userUuid 用户业务ID
     * @return 根目录
     */
    Resource findRootFolderByUserUuid(String userUuid);

    /**
     * 查询用户指定目录。
     *
     * @param userUuid 用户业务ID
     * @param resourceUuid 目录资源业务ID
     * @return 目录实体
     */
    Resource findFolderByResourceUuid(@Param("userUuid") String userUuid, @Param("resourceUuid") String resourceUuid);

    /**
     * 查询用户指定资源（含已删除）。
     *
     * @param userUuid 用户业务ID
     * @param resourceUuid 资源业务ID
     * @return 资源实体
     */
    Resource findByResourceUuid(@Param("userUuid") String userUuid, @Param("resourceUuid") String resourceUuid);

    /**
     * 查询用户指定资源（文件或目录）。
     *
     * @param userUuid 用户业务ID
     * @param resourceUuid 资源业务ID
     * @return 资源实体
     */
    Resource findActiveByResourceUuid(@Param("userUuid") String userUuid, @Param("resourceUuid") String resourceUuid);

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
    List<Resource> listByParent(
            @Param("userUuid") String userUuid,
            @Param("parentId") Long parentId,
            @Param("keyword") String keyword,
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize);

    /**
     * 查询指定父目录下全部未删除子资源。
     */
    List<Resource> listActiveChildrenByParentId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("parentId") Long parentId);

    /**
     * 新增根目录。
     *
     * @param entity 根目录实体
     * @return 影响行数
     */
    int insertRootFolder(Resource entity);

    /**
     * 根据主键查询资源。
     *
     * @param id 主键
     * @return 资源实体
     */
    Resource findById(Long id);

    /**
     * 新增文件资源。
     *
     * @param entity 文件资源
     * @return 影响行数
     */
    int insertFileResource(Resource entity);

    /**
     * 新增目录资源。
     *
     * @param entity 目录资源
     * @return 影响行数
     */
    int insertFolderResource(Resource entity);

    /**
     * 更新资源所在目录、名称与路径。
     */
    int updateParentAndNameAndPath(Resource entity);

    /**
     * 更新资源所在目录、名称与路径（含已删除资源）。
     */
    int updateParentAndNameAndPathAnyStatus(Resource entity);

    /**
     * 更新资源名称与路径。
     */
    int updateNameAndPath(Resource entity);

    /**
     * 批量更新后代节点路径前缀。
     */
    int updatePathCachePrefix(
            @Param("ownerUserId") Long ownerUserId,
            @Param("oldPrefix") String oldPrefix,
            @Param("newPrefix") String newPrefix);

    /**
     * 批量更新后代节点路径前缀（含已删除资源）。
     */
    int updatePathCachePrefixAll(
            @Param("ownerUserId") Long ownerUserId,
            @Param("oldPrefix") String oldPrefix,
            @Param("newPrefix") String newPrefix);

    /**
     * 逻辑删除单个资源。
     */
    int softDeleteById(@Param("id") Long id);

    /**
     * 按路径前缀逻辑删除后代资源。
     */
    int softDeleteByPathPrefix(
            @Param("ownerUserId") Long ownerUserId,
            @Param("pathPrefix") String pathPrefix);

    /**
     * 恢复单个资源。
     */
    int restoreById(@Param("id") Long id);

    /**
     * 按路径前缀恢复后代资源。
     */
    int restoreByPathPrefix(
            @Param("ownerUserId") Long ownerUserId,
            @Param("pathPrefix") String pathPrefix);

    /**
     * 统计父目录下同名资源数量（未删除）。
     *
     * @param parentId 父目录主键
     * @param nameNormalized 标准化名称
     * @return 数量
     */
    Integer countActiveByParentAndNameNormalized(@Param("parentId") Long parentId, @Param("nameNormalized") String nameNormalized);

    /**
     * 统计父目录下同名资源数量（排除指定资源）。
     */
    Integer countActiveByParentAndNameNormalizedExcludeId(
            @Param("parentId") Long parentId,
            @Param("nameNormalized") String nameNormalized,
            @Param("excludeId") Long excludeId);

    /**
     * 查询资源对应的回收站记录（未恢复）。
     */
    RecycleEntry findActiveRecycleEntryByResourceId(@Param("resourceId") Long resourceId);

    /**
     * 新增回收站记录。
     */
    int insertRecycleEntry(RecycleEntry entry);

    /**
     * 标记回收站记录已恢复。
     */
    int markRecycleEntryRestored(@Param("id") Long id);

    /**
     * 统计用户回收站条目（根级资源）。
     */
    Long countRecycleBin(@Param("userUuid") String userUuid, @Param("keyword") String keyword);

    /**
     * 分页查询用户回收站（根级资源）。
     */
    List<RecycleBinRow> listRecycleBin(
            @Param("userUuid") String userUuid,
            @Param("keyword") String keyword,
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize);

    /**
     * 列出用户回收站中全部根资源的 resource_uuid（用于清空回收站）。
     */
    List<String> listRecycleRootResourceUuids(@Param("userUuid") String userUuid);

    /**
     * 列出待物理删除的子树（仅已逻辑删除的资源），按路径从深到浅排序。
     */
    List<Resource> listDeletedSubtreeForPurge(
            @Param("ownerUserId") Long ownerUserId,
            @Param("rootId") Long rootId,
            @Param("folderPathPrefix") String folderPathPrefix);

    /**
     * 统计仍引用某存储对象的文件资源数量。
     */
    Long countByObjectId(@Param("objectId") Long objectId);

    /**
     * 物理删除资源行。
     */
    int hardDeleteById(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId);

    /**
     * 删除回收站记录（未恢复）。
     */
    int deleteRecycleEntryByResourceId(@Param("resourceId") Long resourceId);

    int deleteResourceAclByResourceIds(@Param("ids") List<Long> ids);

    int deleteShareResourcesByResourceIds(@Param("ids") List<Long> ids);

    int nullifyUploadSessionParentByResourceIds(@Param("ids") List<Long> ids);

    /**
     * 查询用户名下全部未删除的目录资源。
     *
     * @param userUuid 用户业务ID
     * @return 目录列表
     */
    List<Resource> listFoldersByUserUuid(String userUuid);
}

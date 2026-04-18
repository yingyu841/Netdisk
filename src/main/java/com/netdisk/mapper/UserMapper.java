package com.netdisk.mapper;

import com.netdisk.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问接口。
 */
@Mapper
public interface UserMapper {
    /**
     * 新增用户。
     *
     * @param user 用户实体
     * @return 影响行数
     */
    int insert(User user);

    /**
     * 根据用户业务ID查询。
     *
     * @param userUuid 用户业务ID
     * @return 用户实体
     */
    User findByUserUuid(String userUuid);

    /**
     * 根据邮箱查询用户。
     *
     * @param email 邮箱
     * @return 用户实体
     */
    User findByEmail(String email);

    /**
     * 根据手机号查询用户。
     *
     * @param mobile 手机号
     * @return 用户实体
     */
    User findByMobile(String mobile);

    /**
     * 按邮箱统计用户数量。
     *
     * @param email 邮箱
     * @return 数量
     */
    int countByEmail(String email);

    /**
     * 按手机号统计用户数量。
     *
     * @param mobile 手机号
     * @return 数量
     */
    int countByMobile(String mobile);
}

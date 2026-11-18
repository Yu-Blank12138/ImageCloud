package com.blank.imagecloud.service;

import com.blank.imagecloud.models.vo.entity.UserAccount;
import com.blank.imagecloud.models.dto.auth.RegisterRequest;
import com.blank.imagecloud.models.dto.user.AdminCreateUserRequest;
import com.blank.imagecloud.models.dto.user.UserQueryRequest;
import com.blank.imagecloud.models.dto.user.AdminUpdateUserRequest;
import com.blank.imagecloud.models.vo.AdminUserPageVo;
import com.blank.imagecloud.models.vo.AdminUserVo;
import com.blank.imagecloud.models.vo.PublicUserVo;
import com.blank.imagecloud.models.vo.RegisterVo;

/** 定义用户账号创建、管理写入和资料查询。 */
public interface UserService {

    /**
     * 读取请求认证需要的账号资料。
     *
     * @param id 已验签令牌携带的用户 ID
     * @return 账号实体，账号不存在或已逻辑删除时返回 null
     */
    UserAccount findAccountForAuthentication(Long id);

    /**
     * 创建公开注册的普通账号。
     *
     * @param request 已校验的注册资料
     * @return 新用户的 ID 和登录账号
     */
    RegisterVo register(RegisterRequest request);

    /**
     * 使用默认初始密码和已校验的资料创建管理员指定角色和状态的用户。
     *
     * @param request 管理员提交的用户创建资料
     */
    void createUser(AdminCreateUserRequest request);

    /**
     * 校验请求中的 VIP 信息，直接更新管理员提交的其他用户资料字段。
     *
     * @param operatorId 已通过管理员鉴权的当前用户 ID
     * @param request 管理员提交的局部更新内容
     */
    void updateUser(Long operatorId, AdminUpdateUserRequest request);

    /**
     * 逻辑删除管理员指定的其他用户。
     *
     * @param operatorId 已通过管理员鉴权的当前用户 ID
     * @param id 目标用户 ID
     */
    void deleteUser(Long operatorId, Long id);

    /**
     * 按查询对象中的条件筛选，并按 ID 倒序分页查询未删除的用户资料。
     *
     * @param request 已校验的用户筛选条件及分页参数
     * @return 当前页记录、总数及分页参数
     */
    AdminUserPageVo pageAdminUsers(UserQueryRequest request);

    /**
     * 为已通过管理员校验的调用方查询用户详情。
     *
     * @param id 目标用户 ID
     * @return 管理员可见的用户资料
     */
    AdminUserVo getAdminUser(Long id);

    /**
     * 查询正常账号的公开资料。
     *
     * @param id 目标用户 ID
     * @return 公开的用户基本资料
     */
    PublicUserVo getPublicUser(Long id);
}

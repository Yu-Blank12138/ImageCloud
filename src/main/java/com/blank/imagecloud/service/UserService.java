package com.blank.imagecloud.service;

import com.blank.imagecloud.models.entity.UserAccount;
import com.blank.imagecloud.models.dto.auth.RegisterRequest;
import com.blank.imagecloud.models.dto.user.AdminCreateUserRequest;
import com.blank.imagecloud.models.dto.user.PublicUserSearchRequest;
import com.blank.imagecloud.models.dto.user.UserQueryRequest;
import com.blank.imagecloud.models.dto.user.UserUpdateRequest;
import com.blank.imagecloud.models.dto.user.UserPasswordUpdateRequest;
import com.blank.imagecloud.models.dto.user.AdminUpdateUserRequest;
import com.blank.imagecloud.models.vo.user.AdminUserPageVo;
import com.blank.imagecloud.models.vo.user.AdminUserVo;
import com.blank.imagecloud.models.dto.user.UserUpdateRequest;
import com.blank.imagecloud.models.vo.user.PublicUserSearchPageVo;
import com.blank.imagecloud.models.vo.user.PublicUserSearchVo;
import com.blank.imagecloud.models.vo.user.PublicUserVo;
import com.blank.imagecloud.models.vo.user.UserAvatarUploadVo;
import com.blank.imagecloud.models.vo.auth.RegisterVo;
import org.springframework.web.multipart.MultipartFile;

/** 定义用户账号创建、管理写入和资料查询。 */
public interface UserService {
    /**
     * 查询指定用户当前头像对应的公开原图地址。
     *
     * @param id 目标用户 ID
     * @return 可直接加载的固定原图 URL
     */
    String getOriginalAvatarUrl(Long id);

    /**
     * 上传当前用户的头像并返回长期可访问的固定 URL。
     *
     * @param userId 当前登录用户 ID
     * @param file 上传的图像文件
     * @return 公开可读的压缩头像和原图固定 URL
     */
    UserAvatarUploadVo uploadOwnImage(Long userId, MultipartFile file);

    /**
     * 更新当前用户提交的非空个人资料字段。
     *
     * @param userId 当前登录用户 ID
     * @param request 已校验的个人资料
     */
    void updateOwnProfile(Long userId, UserUpdateRequest request);

    /**
     * 校验当前密码并替换为新密码哈希。
     *
     * @param userId 当前登录用户 ID
     * @param request 已校验的新旧密码
     */
    void updateOwnPassword(Long userId, UserPasswordUpdateRequest request);

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

    /**
     * 按昵称分页搜索正常账号，昵称完全一致和有可用图片的候选排在前面。
     *
     * @param request 昵称关键词与分页参数
     * @return 包含当前页候选、总数和分页参数的结果
     */
    PublicUserSearchPageVo searchPublicUsers(PublicUserSearchRequest request);
}

package com.blank.imagecloud.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blank.imagecloud.common.CurrentUserContext;
import com.blank.imagecloud.common.ThrowUtils;
import com.blank.imagecloud.constant.UserConstant;
import com.blank.imagecloud.exception.ErrorCode;
import com.blank.imagecloud.mapper.UserAccountMapper;
import com.blank.imagecloud.models.dto.auth.RegisterRequest;
import com.blank.imagecloud.models.dto.user.AdminCreateUserRequest;
import com.blank.imagecloud.models.dto.user.AdminUpdateUserRequest;
import com.blank.imagecloud.models.dto.user.UserQueryRequest;
import com.blank.imagecloud.models.vo.entity.UserAccount;
import com.blank.imagecloud.service.UserService;
import com.blank.imagecloud.models.vo.AdminUserPageVo;
import com.blank.imagecloud.models.vo.AdminUserVo;
import com.blank.imagecloud.models.vo.PublicUserVo;
import com.blank.imagecloud.models.vo.RegisterVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** 实现账号创建、用户管理写入和用户资料查询。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 查询当前账号，供内部认证流程直接使用账号实体。
     *
     * @param id 已验签令牌携带的用户 ID
     * @return 最新账号实体，账号不存在或已逻辑删除时返回 null
     */
    @Override
    public UserAccount findAccountForAuthentication(Long id) {
        return userAccountMapper.selectById(id);
    }

    /**
     * 使用公开注册的默认角色和状态创建账号。
     *
     * @param request 已校验的注册资料
     * @return 新用户的 ID 和登录账号
     */
    @Override
    public RegisterVo register(RegisterRequest request) {
        UserAccount account = insertAccount(request.getAccount(), request.getPassword(), request.getDisplayName(),
                UserConstant.ROLE_USER, UserConstant.STATUS_ACTIVE, null);
        // 写入成功后记录账号标识，日志中不包含密码或哈希。
        log.info("User registered: userId={}, account={}", account.getId(), account.getAccount());
        return new RegisterVo(account.getId(), account.getAccount());
    }

    /**
     * 使用默认初始密码创建管理员指定角色和状态的账号，单条插入使用自动提交。
     *
     * @param request 已校验的管理创建资料
     */
    @Override
    public void createUser(AdminCreateUserRequest request) {
        String role = request.getPlatformRole() == null ? UserConstant.ROLE_USER : request.getPlatformRole();
        String status = request.getStatus() == null ? UserConstant.STATUS_ACTIVE : request.getStatus();
        UserAccount account = insertAccount(request.getAccount(), UserConstant.DEFAULT_PASSWORD, request.getDisplayName(),
                role, status, toDatabaseTime(request.getVipExpiresAt()));
        log.info("Admin created user: operatorId={}, userId={}, account={}, role={}, status={}",
                CurrentUserContext.getUserId(), account.getId(), account.getAccount(), role, status);
    }

    /**
     * 校验本次请求的 VIP 信息并直接更新，通过影响行数判断目标是否存在。
     *
     * @param operatorId 已通过管理员鉴权的当前用户 ID
     * @param request 已校验的局部更新内容
     */
    @Override
    public void updateUser(Long operatorId, AdminUpdateUserRequest request) {
        Long id = request.getId();
        validateAdminTarget(operatorId, id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(request, "id"), ErrorCode.VALIDATION_FAILED, "至少提交一个可更新字段");

        // 设置 VIP 角色时，本次请求必须同时提供到期时间。
        validateVipExpiry(request.getPlatformRole(), toDatabaseTime(request.getVipExpiresAt()));

        // 更新实体只携带请求提交的非空字段，避免并发时覆盖其他请求修改的字段。
        UserAccount changes = new UserAccount();
        BeanUtil.copyProperties(request, changes, CopyOptions.create().ignoreNullValue()
                .setPropertiesFilter((field, value) -> Set.of("id", "account", "displayName",
                        "platformRole", "status", "vipExpiresAt").contains(field.getName()))
                .setConverter((type, value) -> value instanceof Instant instant ? toDatabaseTime(instant) : value));
        changes.setUpdateTime(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS));
        try {
            int updated = userAccountMapper.updateById(changes);
            ThrowUtils.throwIf(updated != 1, ErrorCode.RESOURCE_NOT_FOUND);
        } catch (DuplicateKeyException exception) {
            ThrowUtils.throwException(ErrorCode.CONFLICT, "登录账号已被占用");
        }
        log.info("Admin updated user: operatorId={}, userId={}", operatorId, id);
    }

    /**
     * 校验目标为其他用户，并通过 MyBatis-Plus 配置按 ID 执行逻辑删除。
     *
     * @param operatorId 已通过管理员鉴权的当前用户 ID
     * @param id 目标用户 ID
     */
    @Override
    public void deleteUser(Long operatorId, Long id) {
        validateAdminTarget(operatorId, id);
        int deleted = userAccountMapper.deleteById(id);
        ThrowUtils.throwIf(deleted == 0, ErrorCode.RESOURCE_NOT_FOUND);
        log.info("Admin deleted user: operatorId={}, userId={}", operatorId, id);
    }

    /**
     * 按已校验的用户查询条件构建 Wrapper，由分页插件复用条件查询总数和记录。
     *
     * @param request 用户筛选条件和继承的分页参数
     * @return 当前页、总数及分页参数
     */
    @Override
    public AdminUserPageVo pageAdminUsers(UserQueryRequest request) {
        ThrowUtils.throwIf(request.getCreatedAtStart() != null && request.getCreatedAtEnd() != null
                        && request.getCreatedAtStart().isAfter(request.getCreatedAtEnd()),
                ErrorCode.VALIDATION_FAILED, "创建时间下限不能晚于上限");
        // account 列使用区分大小写的 ascii_bin，显式转义下划线，搜索值通过占位参数绑定。
        var query = adminUserFields()
                .eq(request.getId() != null, UserAccount::getId, request.getId())
                .apply(request.getAccount() != null, "account LIKE {0} ESCAPE '!'",
                        containsPattern(request.getAccount()))
                .apply(request.getDisplayName() != null, "display_name LIKE {0} ESCAPE '!'",
                        containsPattern(request.getDisplayName()))
                .eq(request.getPlatformRole() != null, UserAccount::getPlatformRole, request.getPlatformRole())
                .eq(request.getStatus() != null, UserAccount::getStatus, request.getStatus())
                .ge(request.getCreatedAtStart() != null, UserAccount::getCreatedAt,
                        toDatabaseTime(request.getCreatedAtStart()))
                .le(request.getCreatedAtEnd() != null, UserAccount::getCreatedAt,
                        toDatabaseTime(request.getCreatedAtEnd()))
                .orderByDesc(UserAccount::getId);
        Page<UserAccount> result = userAccountMapper.selectPage(new Page<>(request.getPage(), request.getPageSize()),
                query);
        return new AdminUserPageVo(result.getRecords().stream().map(UserServiceImpl::toAdminUser).toList(),
                result.getTotal(), request.getPage(), request.getPageSize());
    }

    /**
     * 将名称片段转换为字面包含匹配参数，转义 SQL LIKE 的特殊字符。
     *
     * @param value 已规整的账号或昵称片段，为 null 时表示未指定条件
     * @return 包含前后通配符的转义参数，未指定条件时返回 null
     */
    private static String containsPattern(String value) {
        return value == null ? null : "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    /**
     * 查询未删除用户的完整管理资料，包括停用账号。
     *
     * @param id 目标用户 ID
     * @return 管理员可见的用户详情
     */
    @Override
    public AdminUserVo getAdminUser(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.VALIDATION_FAILED);
        UserAccount account = userAccountMapper.selectOne(adminUserFields().eq(UserAccount::getId, id));
        ThrowUtils.throwIf(account == null, ErrorCode.RESOURCE_NOT_FOUND);
        return toAdminUser(account);
    }

    /**
     * 查询正常且未删除账号的公开基本资料。
     *
     * @param id 目标用户 ID
     * @return 用户 ID、昵称以及可公开访问的头像地址
     */
    @Override
    public PublicUserVo getPublicUser(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.VALIDATION_FAILED);
        UserAccount account = userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .select(UserAccount::getId, UserAccount::getDisplayName)
                .eq(UserAccount::getId, id)
                .eq(UserAccount::getStatus, UserConstant.STATUS_ACTIVE));
        ThrowUtils.throwIf(account == null, ErrorCode.RESOURCE_NOT_FOUND);
        //TODO COS 访问签名接入后，再向公开接口提供短期头像地址。
        return new PublicUserVo(account.getId(), account.getDisplayName(), null);
    }

    /**
     * 构建管理员查询共用的字段白名单。
     *
     * @return 选取管理员可见字段的查询条件
     */
    private static LambdaQueryWrapper<UserAccount> adminUserFields() {
        return Wrappers.<UserAccount>lambdaQuery()
                .select(UserAccount::getId, UserAccount::getAccount, UserAccount::getDisplayName,
                        UserAccount::getAvatarUrl, UserAccount::getPlatformRole, UserAccount::getStatus,
                        UserAccount::getVipExpiresAt, UserAccount::getIsDelete, UserAccount::getCreatedAt,
                        UserAccount::getEditTime, UserAccount::getUpdateTime);
    }

    /**
     * 将查询投影转换为管理接口共用的用户资料。
     *
     * @param account 通过字段白名单读出的用户记录
     * @return 管理员可见的用户资料
     */
    private static AdminUserVo toAdminUser(UserAccount account) {
        return new AdminUserVo(account.getId(), account.getAccount(), account.getDisplayName(),
                account.getAvatarUrl(), account.getPlatformRole(), account.getStatus(),
                toUtcInstant(account.getVipExpiresAt()), account.getIsDelete(),
                toUtcInstant(account.getCreatedAt()), toUtcInstant(account.getEditTime()),
                toUtcInstant(account.getUpdateTime()));
    }

    /**
     * 对密码做哈希并初始化账号时间，编辑时间初始为创建时间。
     *
     * @param loginAccount 区分大小写的登录账号
     * @param password 原始密码
     * @param displayName 可选昵称
     * @param role 账号的平台角色
     * @param status 账号的初始状态
     * @param vipExpiry 按 UTC 存放的 VIP 到期时间，可为空
     * @return 已写入且带有生成主键的账号
     */
    private UserAccount insertAccount(String loginAccount, String password, String displayName,
                                      String role, String status, LocalDateTime vipExpiry) {
        validateVipExpiry(role, vipExpiry);
        UserAccount account = new UserAccount();
        account.setAccount(loginAccount);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setDisplayName(StringUtils.hasText(displayName) ? displayName : null);
        account.setPlatformRole(role);
        account.setStatus(status);
        account.setVipExpiresAt(vipExpiry);
        account.setIsDelete(UserConstant.NOT_DELETED);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        account.setCreatedAt(now);
        account.setEditTime(now);
        account.setUpdateTime(now);
        try {
            userAccountMapper.insert(account);
        } catch (DuplicateKeyException exception) {
            ThrowUtils.throwException(ErrorCode.CONFLICT, "登录账号已被占用");
        }
        return account;
    }

    /**
     * 校验管理员写入请求的目标 ID，并禁止通过管理接口操作自己的账号。
     *
     * @param operatorId 已通过管理员鉴权的当前用户 ID
     * @param id 请求中的目标用户 ID
     */
    private static void validateAdminTarget(Long operatorId, Long id) {
        ThrowUtils.throwIf(operatorId == null || operatorId <= 0, ErrorCode.INVALID_ACCESS_TOKEN);
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.VALIDATION_FAILED);
        ThrowUtils.throwIf(id.equals(operatorId), ErrorCode.ACCESS_DENIED, "不能通过管理接口操作自己的账号");
    }

    /**
     * 确保最终角色为 VIP 的用户有到期时间。
     *
     * @param role 写入后的角色
     * @param vipExpiry 写入后的到期时间，可为空
     */
    private static void validateVipExpiry(String role, LocalDateTime vipExpiry) {
        ThrowUtils.throwIf(UserConstant.ROLE_VIP.equals(role) && vipExpiry == null,
                ErrorCode.VALIDATION_FAILED, "VIP 用户必须设置到期时间");
    }

    /**
     * 将客户端时间转换为数据库按 UTC 保存的时间。
     *
     * @param value 客户端提交的时间点，可为空
     * @return 数据库保存的 UTC 时间，原值为空时返回 null
     */
    private static LocalDateTime toDatabaseTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 将数据库中按 UTC 存放的时间转换成时间点。
     *
     * @param value 数据库时间，可为空
     * @return 对应的 UTC 时间点，原值为空时返回 null
     */
    private static Instant toUtcInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}

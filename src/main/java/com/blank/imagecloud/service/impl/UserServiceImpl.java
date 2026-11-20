package com.blank.imagecloud.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blank.imagecloud.common.CurrentUserContext;
import com.blank.imagecloud.common.ThrowUtils;
import com.blank.imagecloud.constant.UserConstant;
import com.blank.imagecloud.config.CosConfig;
import com.blank.imagecloud.exception.ErrorCode;
import com.blank.imagecloud.mapper.UserAccountMapper;
import com.blank.imagecloud.models.dto.auth.RegisterRequest;
import com.blank.imagecloud.models.dto.user.AdminCreateUserRequest;
import com.blank.imagecloud.models.dto.user.AdminUpdateUserRequest;
import com.blank.imagecloud.models.dto.user.PublicUserSearchRequest;
import com.blank.imagecloud.models.dto.user.UserQueryRequest;
import com.blank.imagecloud.models.dto.user.UserUpdateRequest;
import com.blank.imagecloud.models.dto.user.UserPasswordUpdateRequest;
import com.blank.imagecloud.models.entity.UserAccount;
import com.blank.imagecloud.service.ImageService;
import com.blank.imagecloud.service.UserService;
import com.blank.imagecloud.utils.CosUtils;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.CannedAccessControlList;
import com.blank.imagecloud.models.vo.user.AdminUserPageVo;
import com.blank.imagecloud.models.vo.user.AdminUserVo;
import com.blank.imagecloud.models.vo.user.PublicUserSearchPageVo;
import com.blank.imagecloud.models.vo.user.PublicUserSearchVo;
import com.blank.imagecloud.models.vo.user.PublicUserVo;
import com.blank.imagecloud.models.vo.user.UserAvatarUploadVo;
import com.blank.imagecloud.models.vo.auth.RegisterVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 实现账号创建、用户管理写入和用户资料查询。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    /** 用户搜索结果中每人展示的图片预览数量。 */
    private static final int SEARCH_PREVIEW_IMAGE_LIMIT = 3;

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final CosUtils cosUtils;
    private final COSClient cosClient;
    private final CosConfig cosConfig;
    private final ImageService imageService;

    /**
     * 上传公开原图和展示图，将两个固定地址返回给前端保存个人资料。
     *
     * @param userId 当前登录用户 ID，用于认证和操作日志
     * @param file 上传的图像，大小和格式由 COS 工具校验
     * @return 压缩头像和原图的固定 URL
     */
    @Override
    public UserAvatarUploadVo uploadOwnImage(Long userId, MultipartFile file) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.INVALID_ACCESS_TOKEN);
        var uploaded = cosUtils.uploadImage(file, "avatars/");
        String url = null;
        String originalUrl = null;
        try {
            cosClient.setObjectAcl(cosConfig.getBucket(), uploaded.getOriginalObjectKey(),
                    CannedAccessControlList.PublicRead);
            cosClient.setObjectAcl(cosConfig.getBucket(), uploaded.getThumbnailObjectKey(),
                    CannedAccessControlList.PublicRead);
            url = cosClient.getObjectUrl(cosConfig.getBucket(), uploaded.getThumbnailObjectKey()).toExternalForm();
            originalUrl = cosClient.getObjectUrl(cosConfig.getBucket(), uploaded.getOriginalObjectKey()).toExternalForm();
        } catch (CosClientException exception) {
            log.error("Failed to publish avatar: userId={}", userId, exception);
            ThrowUtils.throwException(ErrorCode.SERVICE_UNAVAILABLE, "图片访问地址生成失败，请稍后重试");
        }
        log.info("User uploaded image: userId={}, objectKey={}", userId, uploaded.getThumbnailObjectKey());
        return new UserAvatarUploadVo(url, originalUrl);
    }

    /**
     * 从用户表读取当前保存的公开原图固定地址。
     *
     * @param id 目标用户 ID
     * @return 当前头像的公开原图 URL
     */
    @Override
    public String getOriginalAvatarUrl(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.VALIDATION_FAILED);
        UserAccount account = userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .select(UserAccount::getAvatarOriginalUrl).eq(UserAccount::getId, id)
                .eq(UserAccount::getStatus, UserConstant.STATUS_ACTIVE));
        ThrowUtils.throwIf(account == null || !StringUtils.hasText(account.getAvatarOriginalUrl()),
                ErrorCode.RESOURCE_NOT_FOUND, "用户未设置头像原图或账号不可用");
        return account.getAvatarOriginalUrl();
    }

    /**
     * 只复制本人资料白名单中的非空字段，更新语句使用数据库当前本地时间记录编辑时间。
     *
     * @param userId 从登录态取得的当前用户 ID
     * @param request 已校验的个人资料
     */
    @Override
    public void updateOwnProfile(Long userId, UserUpdateRequest request) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.INVALID_ACCESS_TOKEN);
        ThrowUtils.throwIf(BeanUtil.isEmpty(request), ErrorCode.VALIDATION_FAILED, "至少提交一个可更新字段");
        validateBirthMonth(request.getBirthMonth());
        // 登录账号不可修改，更新白名单不含 account，也不会触发账号唯一键冲突。
        UserAccount changes = new UserAccount();
        BeanUtil.copyProperties(request, changes, CopyOptions.create().ignoreNullValue()
                .setPropertiesFilter((field, value) -> Set.of("displayName", "avatarUrl", "avatarOriginalUrl",
                        "gender", "bio", "email", "phoneNumber", "birthMonth")
                        .contains(field.getName())));
        changes.setId(userId);
        // 用户编辑时间与数据库创建、更新时间采用同一时钟，保留单条更新自动提交。
        int updated = userAccountMapper.update(changes, Wrappers.<UserAccount>lambdaUpdate()
                .eq(UserAccount::getId, userId).eq(UserAccount::getStatus, UserConstant.STATUS_ACTIVE)
                .setSql("edit_time = CURRENT_TIMESTAMP(3)"));
        ThrowUtils.throwIf(updated != 1, ErrorCode.INVALID_ACCESS_TOKEN);
        log.info("User updated own profile: userId={}", userId);
    }

    /**
     * 验证原密码，并通过原哈希条件防止并发请求覆盖已经变化的密码。
     *
     * @param userId 从登录态取得的当前用户 ID
     * @param request 已校验的新旧密码
     */
    @Override
    public void updateOwnPassword(Long userId, UserPasswordUpdateRequest request) {
        UserAccount current = findAccountForAuthentication(userId);
        ThrowUtils.throwIf(current == null || !UserConstant.STATUS_ACTIVE.equals(current.getStatus()),
                ErrorCode.INVALID_ACCESS_TOKEN);
        ThrowUtils.throwIf(!passwordEncoder.matches(request.getOldPassword(), current.getPasswordHash()),
                ErrorCode.INVALID_CREDENTIALS, "原密码错误");
        UserAccount changes = new UserAccount();
        changes.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // 单条条件更新使用自动提交，原密码变化后须重新验证。
        int updated = userAccountMapper.update(changes, Wrappers.<UserAccount>lambdaUpdate()
                .eq(UserAccount::getId, userId)
                .eq(UserAccount::getPasswordHash, current.getPasswordHash())
                .eq(UserAccount::getStatus, UserConstant.STATUS_ACTIVE)
                .setSql("edit_time = CURRENT_TIMESTAMP(3)"));
        ThrowUtils.throwIf(updated != 1, ErrorCode.CONFLICT, "账号或密码已变化，请重试");
        log.info("User updated own password: userId={}", userId);
    }

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
                role, status, request.getVipExpiresAt());
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

        validateBirthMonth(request.getBirthMonth());

        String role = request.getPlatformRole();
        // 设置 VIP 角色时，本次请求必须同时提供到期日期；单独提交到期日期会被拒绝。
        validateVipExpiry(role, request.getVipExpiresAt());
        // 降级为非 VIP 时请求不携带到期日期，需要显式置空，避免残留旧会员信息。
        boolean clearExpiry = role != null && !UserConstant.ROLE_VIP.equals(role);

        // 更新实体只携带请求提交的非空字段，避免并发时覆盖其他请求修改的字段；登录账号不可修改。
        UserAccount changes = new UserAccount();
        BeanUtil.copyProperties(request, changes, CopyOptions.create().ignoreNullValue()
                .setPropertiesFilter((field, value) -> Set.of("id", "displayName",
                        "platformRole", "status", "vipExpiresAt", "gender", "bio", "email",
                        "phoneNumber", "birthMonth").contains(field.getName())));
        // 用户编辑时间与数据库创建、更新时间采用同一时钟，保留单条更新自动提交。
        LambdaUpdateWrapper<UserAccount> updateWrapper = Wrappers.<UserAccount>lambdaUpdate()
                .eq(UserAccount::getId, id).setSql("edit_time = CURRENT_TIMESTAMP(3)");
        if (clearExpiry) {
            updateWrapper.set(UserAccount::getVipExpiresAt, null);
        }
        int updated = userAccountMapper.update(changes, updateWrapper);
        ThrowUtils.throwIf(updated != 1, ErrorCode.RESOURCE_NOT_FOUND);
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
        // 只设置创建时间下限时，上限缺省为当前时间，表示查询起点至今的记录。
        LocalDateTime createdAtEnd = request.getCreatedAtEnd();
        if (request.getCreatedAtStart() != null && createdAtEnd == null) {
            createdAtEnd = LocalDateTime.now();
        }
        ThrowUtils.throwIf(request.getCreatedAtStart() != null && createdAtEnd != null
                        && request.getCreatedAtStart().isAfter(createdAtEnd),
                ErrorCode.VALIDATION_FAILED, "创建时间下限不能晚于上限");
        // account 列使用区分大小写的 ascii_bin，显式转义下划线，搜索值通过占位参数绑定。
        var query = adminUserFields()
                .eq(request.getId() != null, UserAccount::getId, request.getId())
                .apply(request.getAccount() != null, "account LIKE {0} ESCAPE '!'",
                        containsPattern(request.getAccount()))
                .apply(request.getDisplayName() != null, "display_name LIKE {0} ESCAPE '!'",
                        containsPattern(request.getDisplayName()))
                .in(request.getPlatformRoles() != null && !request.getPlatformRoles().isEmpty(),
                        UserAccount::getPlatformRole, request.getPlatformRoles())
                .eq(request.getStatus() != null, UserAccount::getStatus, request.getStatus())
                .ge(request.getCreatedAtStart() != null, UserAccount::getCreatedAt,
                        request.getCreatedAtStart())
                .le(createdAtEnd != null, UserAccount::getCreatedAt, createdAtEnd)
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
     * @return 登录账号、公开身份、昵称以及可公开访问的头像地址
     */
    @Override
    public PublicUserVo getPublicUser(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.VALIDATION_FAILED);
        UserAccount account = userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .select(UserAccount::getAccount, UserAccount::getPlatformRole, UserAccount::getVipExpiresAt,
                        UserAccount::getDisplayName, UserAccount::getAvatarUrl,
                        UserAccount::getGender, UserAccount::getBio, UserAccount::getEmail, UserAccount::getBirthMonth)
                .eq(UserAccount::getId, id)
                .eq(UserAccount::getStatus, UserConstant.STATUS_ACTIVE));
        ThrowUtils.throwIf(account == null, ErrorCode.RESOURCE_NOT_FOUND);
        // VIP 到期后不再对外展示会员身份，避免公开资料与权益状态不一致。
        String platformRole = account.getPlatformRole();
        if (UserConstant.ROLE_VIP.equals(platformRole)
                && (account.getVipExpiresAt() == null || account.getVipExpiresAt().isBefore(LocalDate.now()))) {
            platformRole = UserConstant.ROLE_USER;
        }
        return new PublicUserVo(account.getAccount(), platformRole, account.getDisplayName(), account.getAvatarUrl(),
                account.getGender(), account.getBio(), account.getEmail(), account.getBirthMonth());
    }

    /**
     * 按昵称分页搜索正常账号，昵称完全一致和有可用图片的候选排在前面。
     *
     * @param request 昵称关键词与分页参数
     * @return 包含当前页候选、总数和分页参数的结果
     */
    @Override
    public PublicUserSearchPageVo searchPublicUsers(PublicUserSearchRequest request) {
        String keyword = request.getDisplayName() == null ? "" : request.getDisplayName().trim();
        ThrowUtils.throwIf(keyword.isEmpty() || keyword.length() > UserConstant.DISPLAY_NAME_MAX_LENGTH,
                ErrorCode.VALIDATION_FAILED);
        Page<UserAccount> result = userAccountMapper.selectPage(new Page<>(request.getPage(), request.getPageSize()),
                Wrappers.<UserAccount>lambdaQuery()
                        .select(UserAccount::getId, UserAccount::getAccount,
                                UserAccount::getDisplayName, UserAccount::getAvatarUrl)
                        .like(UserAccount::getDisplayName, keyword)
                        .eq(UserAccount::getStatus, UserConstant.STATUS_ACTIVE));
        List<UserAccount> accounts = result.getRecords();
        // 一次 IN 查询取回当前页所有用户的图片预览。
        Map<Long, List<String>> previews = imageService.listLatestApprovedThumbnailUrls(
                accounts.stream().map(UserAccount::getId).toList(), SEARCH_PREVIEW_IMAGE_LIMIT);
        List<PublicUserSearchVo> records = new ArrayList<>(accounts.size());
        for (UserAccount account : accounts) {
            records.add(new PublicUserSearchVo(account.getId(), account.getAccount(), account.getDisplayName(),
                    account.getAvatarUrl(), previews.getOrDefault(account.getId(), List.of())));
        }
        // 昵称完全一致的候选排在最前，其次是有可用图片的用户。
        records.sort(Comparator.comparing((PublicUserSearchVo item) -> !keyword.equals(item.getDisplayName()))
                .thenComparing(item -> item.getPreviewImageUrls().isEmpty()));
        return new PublicUserSearchPageVo(records, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 构建管理员查询共用的字段白名单。
     *
     * @return 选取管理员可见字段的查询条件
     */
    private static LambdaQueryWrapper<UserAccount> adminUserFields() {
        return Wrappers.<UserAccount>lambdaQuery()
                .select(UserAccount::getId, UserAccount::getAccount, UserAccount::getDisplayName,
                        UserAccount::getAvatarUrl, UserAccount::getAvatarOriginalUrl,
                        UserAccount::getPlatformRole, UserAccount::getStatus,
                        UserAccount::getVipExpiresAt, UserAccount::getIsDelete, UserAccount::getCreatedAt,
                        UserAccount::getEditTime, UserAccount::getUpdateTime, UserAccount::getGender, UserAccount::getBio,
                        UserAccount::getEmail, UserAccount::getPhoneNumber, UserAccount::getBirthMonth);
    }

    /**
     * 将查询投影转换为管理接口共用的用户资料。
     *
     * @param account 通过字段白名单读出的用户记录
     * @return 管理员可见的用户资料
     */
    private static AdminUserVo toAdminUser(UserAccount account) {
        return new AdminUserVo(account.getId(), account.getAccount(), account.getDisplayName(),
                account.getAvatarUrl(), account.getAvatarOriginalUrl(), account.getPlatformRole(), account.getStatus(),
                account.getVipExpiresAt(), account.getIsDelete(),
                account.getCreatedAt(), account.getEditTime(),
                account.getUpdateTime(), account.getGender(), account.getBio(), account.getEmail(),
                account.getPhoneNumber(), account.getBirthMonth());
    }

    /**
     * 校验已通过格式检查的出生年月，允许空字符串清空。
     *
     * @param birthMonth YYYY-MM 格式的出生年月，可为 null 或空字符串
     */
    private static void validateBirthMonth(String birthMonth) {
        ThrowUtils.throwIf(StringUtils.hasText(birthMonth)
                        && YearMonth.parse(birthMonth).isAfter(YearMonth.now()),
                ErrorCode.VALIDATION_FAILED, "出生年月不能晚于当前年月");
    }

    /**
     * 对密码做哈希并创建账号，创建、初始编辑和更新时间由数据库生成。
     *
     * @param loginAccount 区分大小写的登录账号
     * @param password 原始密码
     * @param displayName 可选昵称
     * @param role 账号的平台角色
     * @param status 账号的初始状态
     * @param vipExpiry 本地日期表示的 VIP 到期日期，可为空
     * @return 已写入且带有生成主键的账号
     */
    private UserAccount insertAccount(String loginAccount, String password, String displayName,
                                      String role, String status, LocalDate vipExpiry) {
        validateVipExpiry(role, vipExpiry);
        UserAccount account = new UserAccount();
        account.setAccount(loginAccount);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setDisplayName(StringUtils.hasText(displayName) ? displayName : null);
        account.setPlatformRole(role);
        account.setStatus(status);
        account.setVipExpiresAt(vipExpiry);
        account.setIsDelete(UserConstant.NOT_DELETED);
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
     * 校验角色与到期日期必须成对出现：VIP 必须有到期日期，非 VIP 不能携带到期日期。
     *
     * @param role 本次请求写入的角色，未修改角色时为空
     * @param vipExpiry 本次请求写入的到期日期，可为空
     */
    private static void validateVipExpiry(String role, LocalDate vipExpiry) {
        if (UserConstant.ROLE_VIP.equals(role)) {
            ThrowUtils.throwIf(vipExpiry == null, ErrorCode.VALIDATION_FAILED, "VIP 用户必须设置到期日期");
            return;
        }
        // 单独提交到期日期而不声明 VIP 身份，会让普通用户残留会员信息。
        ThrowUtils.throwIf(vipExpiry != null, ErrorCode.VALIDATION_FAILED, "只有 VIP 用户才能设置到期日期");
    }

}

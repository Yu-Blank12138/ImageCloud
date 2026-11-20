package com.blank.imagecloud.aop;

import com.blank.imagecloud.annotation.AuthCheck;
import com.blank.imagecloud.common.CurrentUserContext;
import com.blank.imagecloud.common.ThrowUtils;
import com.blank.imagecloud.constant.PlatformRole;
import com.blank.imagecloud.constant.UserConstant;
import com.blank.imagecloud.exception.ErrorCode;
import com.blank.imagecloud.models.entity.UserAccount;
import com.blank.imagecloud.service.UserService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 在标注了权限要求的接口方法执行前验证当前用户。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuthCheckAspect {
    private final UserService userService;

    /**
     * 按当前操作者 ID 查询最新账号状态，并检查方法要求的角色。
     *
     * @param authCheck 目标方法声明的最低平台角色
     */
    @Before(value = "@annotation(authCheck)", argNames = "authCheck")
    public void checkPermission(AuthCheck authCheck) {
        Long userId = CurrentUserContext.requireUserId();
        UserAccount user = userService.findAccountForAuthentication(userId);
        ThrowUtils.throwIf(user == null || !UserConstant.STATUS_ACTIVE.equals(user.getStatus()),
                ErrorCode.INVALID_ACCESS_TOKEN);
        ThrowUtils.throwIf(!hasRequiredRole(user, authCheck.value()), ErrorCode.ACCESS_DENIED);
    }

    /**
     * 按当前账号的角色和会员状态判断是否满足最低权限。
     *
     * @param user 数据库中当前用户的最新账号记录
     * @param requiredRole 接口声明的最低平台角色
     * @return 当前用户具备所需角色权限时返回 true
     */
    private static boolean hasRequiredRole(UserAccount user, PlatformRole requiredRole) {
        String role = user.getPlatformRole();
        boolean admin = UserConstant.ROLE_ADMIN.equals(role);
        boolean vip = UserConstant.ROLE_VIP.equals(role);
        return switch (requiredRole) {
            case USER -> admin || vip || UserConstant.ROLE_USER.equals(role);
            case VIP -> admin || (vip && user.getVipExpiresAt() != null
                    && !user.getVipExpiresAt().isBefore(LocalDate.now()));
            case ADMIN -> admin;
        };
    }
}

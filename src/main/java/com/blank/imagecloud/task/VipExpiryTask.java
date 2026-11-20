package com.blank.imagecloud.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blank.imagecloud.constant.UserConstant;
import com.blank.imagecloud.mapper.UserAccountMapper;
import com.blank.imagecloud.models.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * VIP 会员到期任务，每天零点把已过期会员降级为普通用户并清空到期日期。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VipExpiryTask {

    /**
     * 每天零点执行，使用应用本地时区。
     */
    private static final String DAILY_MIDNIGHT_CRON = "0 0 0 * * ?";

    private final UserAccountMapper userAccountMapper;

    /**
     * 扫描到期日期早于今天的 VIP 账号，将其身份改为普通用户并清空到期日期。
     * 使用「早于今天」而非「等于昨天」，某天执行失败或服务停机后仍能在下次运行补齐。
     * 单条更新语句自动提交，逻辑删除的账号由 MyBatis-Plus 自动排除。
     */
    @Scheduled(cron = DAILY_MIDNIGHT_CRON)
    public void downgradeExpiredVip() {
        LocalDate today = LocalDate.now();
        int updated = userAccountMapper.update(Wrappers.<UserAccount>lambdaUpdate()
                .eq(UserAccount::getPlatformRole, UserConstant.ROLE_VIP)
                .lt(UserAccount::getVipExpiresAt, today)
                .set(UserAccount::getPlatformRole, UserConstant.ROLE_USER)
                .set(UserAccount::getVipExpiresAt, null));
        log.info("Downgraded expired VIP users: count={}, expiredBefore={}", updated, today);
    }
}

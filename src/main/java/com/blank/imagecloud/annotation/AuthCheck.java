package com.blank.imagecloud.annotation;

import com.blank.imagecloud.constant.PlatformRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口方法需要有效登录态以及最低平台角色。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 指定调用该方法所需的最低平台角色。
     *
     * @return 最低平台角色，默认为普通用户
     */
    PlatformRole value() default PlatformRole.USER;
}

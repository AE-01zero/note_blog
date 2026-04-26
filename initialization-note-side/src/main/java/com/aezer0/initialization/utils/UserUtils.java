package com.aezer0.initialization.utils;

import cn.dev33.satoken.stp.StpUtil;
import com.aezer0.initialization.config.exception.BizException;
import com.aezer0.initialization.domain.User;
import com.aezer0.initialization.enums.RoleType;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class UserUtils {

    public static final String DEFAULT_ADMIN_PHONE = "13800000000";

    public static User getCurrentUser() {
        User userObj = (User) StpUtil.getSession().get("user_info");
        if (userObj == null) {
            throw new BizException("用户信息未找到，请重新登录");
        }

        return userObj;
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    public static Integer getCurrentUserRoleType() {
        return getCurrentUser().getRoleType();
    }

    public static Boolean isAdmin() {
        return Objects.equals(getCurrentUserRoleType(), RoleType.ADMIN.getValue());
    }

    public static Boolean isDefaultAdmin() {
        User currentUser = getCurrentUser();
        return Objects.equals(currentUser.getRoleType(), RoleType.ADMIN.getValue())
                && Objects.equals(currentUser.getPhone(), DEFAULT_ADMIN_PHONE);
    }

    public static void requireDefaultAdmin() {
        if (!Boolean.TRUE.equals(isDefaultAdmin())) {
            throw new BizException("仅默认管理员可操作");
        }
    }
}

package com.grun.calorietracker.security;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AdminMfaAccessPolicy {
    @Value("${grun.security.admin-mfa-required:false}")
    private boolean required;

    public boolean isRequired(UserEntity user) {
        return user.getRole() != null && user.getRole().isAdminRole()
                && (user.getRole() == UserRole.OWNER || required);
    }

    public boolean requiresEnrollment(UserEntity user) {
        return isRequired(user) && !Boolean.TRUE.equals(user.getAdminMfaEnabled());
    }
}

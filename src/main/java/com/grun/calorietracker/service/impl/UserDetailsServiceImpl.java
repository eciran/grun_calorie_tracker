package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.security.AdminPermissionMatrix;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getAccountEnabled()),
                true,
                true,
                !Boolean.TRUE.equals(user.getAccountLocked()) && !isTemporarilyLoginLocked(user),
                getAuthorities(user.getRole())
        );
    }


    private boolean isTemporarilyLoginLocked(UserEntity user) {
        return user.getLoginLockedUntil() != null && user.getLoginLockedUntil().isAfter(LocalDateTime.now());
    }

    private List<GrantedAuthority> getAuthorities(UserRole role) {
        List<String> authorities = new ArrayList<>();
        authorities.add("ROLE_" + role.name());
        if (role.isAdminRole()) {
            authorities.add("ROLE_ADMIN");
            AdminPermissionMatrix.permissionsFor(role).stream()
                    .map(AdminPermissionMatrix::authority)
                    .forEach(authorities::add);
        }
        return AuthorityUtils.createAuthorityList(authorities.toArray(String[]::new));
    }

}

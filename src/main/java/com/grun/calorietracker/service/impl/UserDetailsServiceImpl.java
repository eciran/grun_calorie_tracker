package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
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
                AuthorityUtils.createAuthorityList("ROLE_" + user.getRole())
        );
    }

    private boolean isTemporarilyLoginLocked(UserEntity user) {
        return user.getLoginLockedUntil() != null && user.getLoginLockedUntil().isAfter(LocalDateTime.now());
    }

    private Collection<? extends GrantedAuthority> getAuthorities(UserRole role) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

}

package com.printflow.service;

import com.printflow.entity.User;
import com.printflow.repository.UserRepository;
import com.printflow.security.CustomUserPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

@Component
@RequestScope
public class CurrentUserContext {

    private final UserRepository userRepository;
    private User cachedUser;
    private Long cachedUserId;
    private Long cachedCompanyId;

    public CurrentUserContext(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Long getUserId() {
        if (cachedUserId != null) {
            return cachedUserId;
        }
        Object principal = getPrincipal();
        if (principal instanceof CustomUserPrincipal p) {
            cachedUserId = p.getUserId();
            cachedCompanyId = p.getCompanyId();
            return cachedUserId;
        }
        if (principal instanceof User user) {
            cacheUser(user);
            return cachedUserId;
        }
        if (principal instanceof UserDetails details) {
            String username = details.getUsername();
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                cacheUser(user);
                return cachedUserId;
            }
        }
        if (principal instanceof String s && !"anonymousUser".equals(s)) {
            User user = userRepository.findByUsername(s).orElse(null);
            if (user != null) {
                cacheUser(user);
                return cachedUserId;
            }
        }
        return null;
    }

    public Long getCompanyId() {
        if (cachedCompanyId != null) {
            return cachedCompanyId;
        }
        Object principal = getPrincipal();
        if (principal instanceof CustomUserPrincipal p) {
            cachedUserId = p.getUserId();
            cachedCompanyId = p.getCompanyId();
            return cachedCompanyId;
        }
        if (cachedUserId != null) {
            cachedCompanyId = userRepository.findCompanyIdByUserId(cachedUserId).orElse(null);
            return cachedCompanyId;
        }
        if (principal instanceof UserDetails details) {
            cachedCompanyId = userRepository.findCompanyIdByUsername(details.getUsername()).orElse(null);
            return cachedCompanyId;
        }
        if (principal instanceof String s && !"anonymousUser".equals(s)) {
            cachedCompanyId = userRepository.findCompanyIdByUsername(s).orElse(null);
            return cachedCompanyId;
        }
        return cachedCompanyId;
    }

    public User getUser() {
        if (cachedUser != null) {
            return cachedUser;
        }
        Object principal = getPrincipal();
        if (principal instanceof User user) {
            cacheUser(user);
            return cachedUser;
        }
        if (principal instanceof CustomUserPrincipal p) {
            User user = userRepository.findById(p.getUserId()).orElse(null);
            if (user != null) {
                cacheUser(user);
            }
            return cachedUser;
        }
        if (principal instanceof UserDetails details) {
            String username = details.getUsername();
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                cacheUser(user);
            }
            return cachedUser;
        }
        if (principal instanceof String s && !"anonymousUser".equals(s)) {
            User user = userRepository.findByUsername(s).orElse(null);
            if (user != null) {
                cacheUser(user);
            }
        }
        return cachedUser;
    }

    private void cacheUser(User user) {
        cachedUser = user;
        cachedUserId = user.getId();
        cachedCompanyId = null;
    }

    private Object getPrincipal() {
        var context = SecurityContextHolder.getContext();
        var auth = context != null ? context.getAuthentication() : null;
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getPrincipal();
    }
}

package com.ecommerce.dreamstore.security;

import com.ecommerce.dreamstore.entity.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthenticatedUserService {

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        throw new AccessDeniedException("User is not authenticated");
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }
}


package com.ecommerce.dreamstore.service.interfaces;

import com.ecommerce.dreamstore.dto.request.LoginRequest;
import com.ecommerce.dreamstore.dto.request.RefreshTokenRequest;
import com.ecommerce.dreamstore.dto.request.RegisterRequest;
import com.ecommerce.dreamstore.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
}


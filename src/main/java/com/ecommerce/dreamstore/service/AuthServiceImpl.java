package com.ecommerce.dreamstore.service;

import com.ecommerce.dreamstore.dto.request.LoginRequest;
import com.ecommerce.dreamstore.dto.request.RegisterRequest;
import com.ecommerce.dreamstore.dto.response.AuthResponse;
import com.ecommerce.dreamstore.entity.Cart;
import com.ecommerce.dreamstore.entity.User;
import com.ecommerce.dreamstore.exception.UserAlreadyExistsException;
import com.ecommerce.dreamstore.repository.CartRepository;
import com.ecommerce.dreamstore.repository.UserRepository;
import com.ecommerce.dreamstore.service.interfaces.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim();
        String phone = request.phone().trim();

        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("User with email '" + email + "' already exists");
        }

        if (userRepository.existsByPhone(phone)) {
            throw new UserAlreadyExistsException("User with phone number '" + phone + "' already exists");
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(request.password()))
                .build();

        user = userRepository.save(user);

        Cart cart = new Cart();
        cart.setUser(user);
        cartRepository.save(cart);

        String accessToken = jwtService.generateToken(user);
        var refreshToken = refreshTokenService.createRefreshToken(user);
        return AuthResponse.of(accessToken, refreshToken.getToken(), jwtService.getExpirationTime());
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = (request.email() != null && !request.email().isBlank())
                ? request.email().trim()
                : request.phone().trim();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(identifier, request.password())
        );

        User user = userRepository.findByEmailOrPhone(identifier, identifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with identifier: " + identifier));

        String accessToken = jwtService.generateToken(user);
        var refreshToken = refreshTokenService.createRefreshToken(user);
        return AuthResponse.of(accessToken, refreshToken.getToken(), jwtService.getExpirationTime());
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(com.ecommerce.dreamstore.dto.request.RefreshTokenRequest request) {
        String requestRefreshToken = request.refreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(com.ecommerce.dreamstore.entity.RefreshToken::getUser)
                .map(user -> {
                    String accessToken = jwtService.generateToken(user);
                    return AuthResponse.of(accessToken, requestRefreshToken, jwtService.getExpirationTime());
                })
                .orElseThrow(() -> new com.ecommerce.dreamstore.exception.TokenRefreshException("Refresh token was not found. Please make a new login request."));
    }
}

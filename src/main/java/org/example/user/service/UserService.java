package org.example.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.user.dto.*;
import org.example.user.entity.RefreshToken;
import org.example.user.entity.Role;
import org.example.user.enums.RoleName;
import org.example.user.entity.User;
import org.example.user.exception.InvalidTokenException;
import org.example.user.exception.UserAlreadyExistsException;
import org.example.user.exception.UserNotFoundException;
import org.example.user.repository.RefreshTokenRepository;
import org.example.user.repository.RoleRepository;
import org.example.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${jwt.refresh-token-ttl-ms:1209600000}")
    private long refreshTokenTtlMs;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.debug("Registration attempt: username={}, email={}", request.username(), request.email());

        if (userRepository.existsByUsername(request.username())) {
            log.warn("Registration failed — username already taken: {}", request.username());
            throw new UserAlreadyExistsException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration failed — email already registered: {}", request.email());
            throw new UserAlreadyExistsException("Email already registered: " + request.email());
        }

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> {
                    log.error("ROLE_USER not found in DB — check data migration");
                    return new IllegalStateException("ROLE_USER not found in DB");
                });

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .roles(Set.of(userRole))
                .build();

        User saved = userRepository.save(user);
        log.info("User registered: userId={}, username={}", saved.getId(), saved.getUsername());
        return toResponse(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.debug("Login attempt: username={}", request.username());

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> {
                    log.warn("Login failed — username not found: {}", request.username());
                    return new BadCredentialsException("Invalid username or password");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed — bad password: userId={}, username={}", user.getId(), user.getUsername());
            throw new BadCredentialsException("Invalid username or password");
        }

        if (!user.isEnabled()) {
            log.warn("Login failed — account disabled: userId={}, username={}", user.getId(), user.getUsername());
            throw new BadCredentialsException("Account is disabled");
        }

        log.info("User logged in: userId={}, username={}", user.getId(), user.getUsername());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        log.debug("Token refresh attempt");

        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> {
                    log.warn("Token refresh failed — token not found");
                    return new InvalidTokenException("Refresh token not found");
                });

        if (!refreshToken.isValid()) {
            log.warn("Token refresh failed — token expired or revoked: userId={}", refreshToken.getUser().getId());
            throw new InvalidTokenException("Refresh token is expired or revoked");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        log.info("Refresh token rotated: userId={}", refreshToken.getUser().getId());
        return issueTokens(refreshToken.getUser());
    }

    @Transactional
    public void logout(UUID userId) {
        log.debug("Logout: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Logout failed — user not found: userId={}", userId);
                    return new UserNotFoundException(userId);
                });

        refreshTokenRepository.revokeAllByUser(user);
        log.info("User logged out, all refresh tokens revoked: userId={}", userId);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        log.debug("Fetching user by id: {}", id);
        return userRepository.findById(id)
                .map(user -> {
                    log.debug("User found: userId={}, username={}", user.getId(), user.getUsername());
                    return toResponse(user);
                })
                .orElseThrow(() -> {
                    log.warn("User not found: userId={}", id);
                    return new UserNotFoundException(id);
                });
    }

    private AuthResponse issueTokens(User user) {
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .toList();

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(Instant.now().plusMillis(refreshTokenTtlMs))
                .build();

        refreshTokenRepository.save(refreshToken);
        log.debug("Tokens issued: userId={}, roles={}", user.getId(), roles);

        return new AuthResponse(accessToken, refreshToken.getToken(), jwtService.getAccessTokenTtlMs());
    }

    private UserResponse toResponse(User user) {
        Set<RoleName> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(java.util.stream.Collectors.toSet());

        return new UserResponse(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                roles, user.isEnabled(), user.getCreatedAt()
        );
    }
}

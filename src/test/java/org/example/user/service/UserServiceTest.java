package org.example.user.service;

import org.example.user.dto.*;
import org.example.user.entity.RefreshToken;
import org.example.user.entity.Role;
import org.example.user.entity.User;
import org.example.user.enums.RoleName;
import org.example.user.exception.InvalidTokenException;
import org.example.user.exception.UserAlreadyExistsException;
import org.example.user.exception.UserNotFoundException;
import org.example.user.repository.RefreshTokenRepository;
import org.example.user.repository.RoleRepository;
import org.example.user.repository.UserRepository;
import org.example.user.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService unit tests")
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    RoleRepository roleRepository;
    @Mock
    RefreshTokenRepository refreshTokenRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtService jwtService;

    @InjectMocks
    UserService userService;

    private UUID userId;
    private Role userRole;
    private User user;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userRole = Role.builder()
                .id(UUID.randomUUID())
                .name(RoleName.ROLE_USER)
                .build();
        user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hashed-password")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(userRole))
                .enabled(true)
                .createdAt(Instant.now())
                .build();
        refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("valid-refresh-token")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
    }

    @Test
    @DisplayName("register: valid request — saves user, assigns ROLE_USER")
    void register_valid_savesUser() {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password123", "Test", "User");

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponse result = userService.register(request);

        assertThat(result.username()).isEqualTo("testuser");
        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.roles()).contains(RoleName.ROLE_USER);
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register: duplicate username — throws UserAlreadyExistsException")
    void register_duplicateUsername_throws() {
        RegisterRequest request = new RegisterRequest("testuser", "other@example.com", "password123", null, null);

        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("testuser");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: duplicate email — throws UserAlreadyExistsException")
    void register_duplicateEmail_throws() {
        RegisterRequest request = new RegisterRequest("newuser", "test@example.com", "password123", null, null);

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("test@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: ROLE_USER missing in DB — throws IllegalStateException")
    void register_roleNotFound_throws() {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password123", null, null);

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROLE_USER");
    }

    @Test
    @DisplayName("register: password is hashed, not stored as plain text")
    void register_passwordIsHashed() {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "plain-password", null, null);

        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("plain-password")).thenReturn("$2a$hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            assertThat(u.getPasswordHash()).isEqualTo("$2a$hashed");
            assertThat(u.getPasswordHash()).doesNotContain("plain-password");
            return user;
        });

        userService.register(request);

        verify(passwordEncoder).encode("plain-password");
    }

    @Test
    @DisplayName("login: valid credentials — returns access and refresh tokens")
    void login_validCredentials_returnsTokens() {
        LoginRequest request = new LoginRequest("testuser", "password123");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), anyString(), any())).thenReturn("access-token");
        when(jwtService.getAccessTokenTtlMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);

        AuthResponse result = userService.login(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.expiresIn()).isEqualTo(900000L);
    }

    @Test
    @DisplayName("login: username not found — throws BadCredentialsException")
    void login_usernameNotFound_throws() {
        LoginRequest request = new LoginRequest("unknown", "password");

        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("login: wrong password — throws BadCredentialsException")
    void login_wrongPassword_throws() {
        LoginRequest request = new LoginRequest("testuser", "wrong-password");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("login: account disabled — throws BadCredentialsException")
    void login_accountDisabled_throws() {
        user = User.builder()
                .id(userId).username("testuser").email("test@example.com")
                .passwordHash("hashed-password").roles(Set.of(userRole))
                .enabled(false).createdAt(Instant.now())
                .build();

        LoginRequest request = new LoginRequest("testuser", "password123");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    @DisplayName("refresh: valid token — revokes old, issues new tokens")
    void refresh_validToken_issuesNewTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");

        when(refreshTokenRepository.findByToken("valid-refresh-token")).thenReturn(Optional.of(refreshToken));
        when(jwtService.generateAccessToken(any(), anyString(), any())).thenReturn("new-access-token");
        when(jwtService.getAccessTokenTtlMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any())).thenReturn(refreshToken);

        AuthResponse result = userService.refresh(request);

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(refreshToken.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any()); // revoke old + save new
    }

    @Test
    @DisplayName("refresh: token not found — throws InvalidTokenException")
    void refresh_tokenNotFound_throws() {
        when(refreshTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.refresh(new RefreshTokenRequest("missing-token")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("refresh: token expired — throws InvalidTokenException")
    void refresh_tokenExpired_throws() {
        RefreshToken expired = RefreshToken.builder()
                .id(UUID.randomUUID()).token("expired-token").user(user)
                .expiryDate(Instant.now().minusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> userService.refresh(new RefreshTokenRequest("expired-token")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("refresh: token revoked — throws InvalidTokenException")
    void refresh_tokenRevoked_throws() {
        RefreshToken revoked = RefreshToken.builder()
                .id(UUID.randomUUID()).token("revoked-token").user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> userService.refresh(new RefreshTokenRequest("revoked-token")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("logout: valid user — revokes all refresh tokens")
    void logout_valid_revokesAllTokens() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.logout(userId);

        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    @DisplayName("logout: user not found — throws UserNotFoundException")
    void logout_notFound_throws() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.logout(userId))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("getById: found — returns UserResponse")
    void getById_found_returnsResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse result = userService.getById(userId);

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.username()).isEqualTo("testuser");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    @DisplayName("getById: not found — throws UserNotFoundException")
    void getById_notFound_throws() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(userId.toString());
    }
}

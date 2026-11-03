package com.travelplatform.service;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.AdminLoginRequest;
import com.travelplatform.dto.AuthResponse;
import com.travelplatform.entity.Admin;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AdminAuthServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AdminRepository admins;
    private JwtUtil jwt;
    private AdminAuthService service;

    @BeforeEach
    void setUp() {
        admins = mock(AdminRepository.class);
        jwt = mock(JwtUtil.class);
        when(jwt.generateToken(anyString(), anyString(), anyLong())).thenReturn("tok");
        when(admins.save(any(Admin.class))).thenAnswer(i -> {
            Admin a = i.getArgument(0);
            a.setId(1L);
            return a;
        });
        service = new AdminAuthService();
        ReflectionTestUtils.setField(service, "adminRepository", admins);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "jwtUtil", jwt);
        ReflectionTestUtils.setField(service, "bootstrapSecret", "s3cret");
    }

    private static AdminLoginRequest req(String email, String password) {
        AdminLoginRequest r = new AdminLoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    @Test
    @DisplayName("bootstrap does not exist while ADMIN_BOOTSTRAP_SECRET is unset")
    void bootstrapIsNotFoundWhenSecretUnset() {
        ReflectionTestUtils.setField(service, "bootstrapSecret", "");
        assertThrows(ResourceNotFoundException.class,
                () -> service.bootstrap(req("a@x.com", "longpassword"), "anything"));
    }

    @Test
    @DisplayName("bootstrap refuses a wrong or missing secret")
    void bootstrapRefusesWrongSecret() {
        assertThrows(BadCredentialsException.class, () -> service.bootstrap(req("a@x.com", "longpassword"), "nope"));
        assertThrows(BadCredentialsException.class, () -> service.bootstrap(req("a@x.com", "longpassword"), null));
        verify(admins, never()).save(any());
    }

    @Test
    @DisplayName("bootstrap works only while no admin exists")
    void bootstrapOnlyWhileNoAdminExists() {
        when(admins.count()).thenReturn(1L);
        assertThrows(IllegalArgumentException.class, () -> service.bootstrap(req("a@x.com", "longpassword"), "s3cret"));
        verify(admins, never()).save(any());
    }

    @Test
    @DisplayName("bootstrap creates the admin with a hashed password and returns an admin token")
    void bootstrapCreatesAdminAndReturnsAdminToken() {
        AuthResponse r = service.bootstrap(req("NammaJourney463@gmail.com", "longpassword"), "s3cret");

        ArgumentCaptor<Admin> saved = ArgumentCaptor.forClass(Admin.class);
        verify(admins).save(saved.capture());
        assertEquals("nammajourney463@gmail.com", saved.getValue().getEmail());
        assertTrue(encoder.matches("longpassword", saved.getValue().getPassword()));
        assertEquals("ROLE_ADMIN", r.getRole());
        assertEquals("tok", r.getToken());
        verify(jwt).generateToken("1", "ROLE_ADMIN", 0L);
    }

    @Test
    @DisplayName("an unknown email and a wrong password get the same answer")
    void loginRejectsUnknownEmailAndWrongPasswordAlike() {
        Admin a = new Admin();
        a.setId(1L);
        a.setEmail("nammajourney463@gmail.com");
        a.setPassword(encoder.encode("rightpassword"));
        when(admins.findByEmailIgnoreCase("nammajourney463@gmail.com")).thenReturn(Optional.of(a));

        BadCredentialsException unknown = assertThrows(BadCredentialsException.class,
                () -> service.login(req("who@x.com", "rightpassword")));
        BadCredentialsException wrong = assertThrows(BadCredentialsException.class,
                () -> service.login(req("nammajourney463@gmail.com", "wrongpassword")));
        assertEquals(unknown.getMessage(), wrong.getMessage());

        assertEquals("ROLE_ADMIN", service.login(req("NammaJourney463@gmail.com", "rightpassword")).getRole());
    }
}

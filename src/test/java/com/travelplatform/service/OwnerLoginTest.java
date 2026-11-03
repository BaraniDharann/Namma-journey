package com.travelplatform.service;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.AuthResponse;
import com.travelplatform.dto.OwnerLoginRequest;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerStatus;
import com.travelplatform.exception.OwnerNotApprovedException;
import com.travelplatform.repository.AdminRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.UserRepository;
import com.travelplatform.security.TokenVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

class OwnerLoginTest {

    final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    OwnerRepository owners;
    JwtUtil jwt;
    TokenVersionService versions;
    OwnerAuthService service;
    Owner owner;

    @BeforeEach
    void setUp() {
        owners = mock(OwnerRepository.class);
        jwt = mock(JwtUtil.class);
        when(jwt.generateToken(anyString(), anyString(), anyLong())).thenAnswer(i -> i.getArgument(1) + "-token");
        when(owners.save(any(Owner.class))).thenAnswer(i -> i.getArgument(0));
        versions = new TokenVersionService(mock(UserRepository.class), mock(DriverRepository.class), owners,
                mock(AdminRepository.class));

        owner = new Owner();
        owner.setId(9L);
        owner.setEmail("ravi@example.com");
        owner.setOwnerCode("NJO-0002");
        owner.setTravelsName("Ravi Travels");
        owner.setStatus(OwnerStatus.APPROVED);
        owner.setPassword(encoder.encode("emailed-pass"));
        when(owners.findByEmailIgnoreCase("ravi@example.com")).thenReturn(Optional.of(owner));
        when(owners.findByOwnerCode("NJO-0002")).thenReturn(Optional.of(owner));
        when(owners.findById(9L)).thenReturn(Optional.of(owner));

        service = new OwnerAuthService();
        ReflectionTestUtils.setField(service, "ownerRepository", owners);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "jwtUtil", jwt);
        ReflectionTestUtils.setField(service, "tokenVersionService", versions);
        ReflectionTestUtils.setField(service, "platformOwnerCode", "NJO-0001");
        ReflectionTestUtils.setField(service, "portalOpenToAllOwners", true);
    }

    @Test
    @DisplayName("until owner screens are scoped per owner, only the platform owner gets an owner token")
    void newOwnersWaitForTheirPortal() {
        ReflectionTestUtils.setField(service, "portalOpenToAllOwners", false);

        // NJO-0002 can still finish setup: the setup token reaches only change-password...
        owner.setMustChangePassword(true);
        assertEquals("ROLE_OWNER_SETUP", service.login(login("NJO-0002", "emailed-pass")).getRole());
        AuthResponse changed = service.changePassword(9L, "emailed-pass", "my-own-password");
        assertNull(changed.getToken(), "no owner token for a new owner yet");
        assertFalse(owner.isMustChangePassword(), "the new password is still saved");

        // ...but never gets ROLE_OWNER, which today sees every owner's bookings and drivers.
        OwnerNotApprovedException e = assertThrows(OwnerNotApprovedException.class,
                () -> service.login(login("NJO-0002", "my-own-password")));
        assertTrue(e.getMessage().contains("opens soon"), e.getMessage());

        owner.setOwnerCode("NJO-0001");
        assertEquals("ROLE_OWNER", service.login(login("ravi@example.com", "my-own-password")).getRole());
    }

    static OwnerLoginRequest login(String id, String password) {
        OwnerLoginRequest r = new OwnerLoginRequest();
        r.setEmail(id);
        r.setPassword(password);
        return r;
    }

    @Test
    @DisplayName("an owner signs in with their Owner ID (any case) or their email")
    void logsInWithOwnerCodeOrEmail() {
        assertEquals("ROLE_OWNER", service.login(login("njo-0002", "emailed-pass")).getRole());
        assertEquals("ROLE_OWNER", service.login(login(" Ravi@Example.com ", "emailed-pass")).getRole());
        AuthResponse r = service.login(login("NJO-0002", "emailed-pass"));
        assertEquals("NJO-0002", r.getOwnerCode());
        assertEquals("Ravi Travels", r.getName());
        assertThrows(BadCredentialsException.class, () -> service.login(login("NJO-0002", "wrong")));
    }

    @Test
    @DisplayName("a pending or rejected owner is told why - but only after the password matched")
    void pendingAndRejectedCannotLogIn() {
        owner.setStatus(OwnerStatus.PENDING_REVIEW);
        OwnerNotApprovedException e = assertThrows(OwnerNotApprovedException.class,
                () -> service.login(login("NJO-0002", "emailed-pass")));
        assertEquals("Your application is under review", e.getMessage());
        assertThrows(BadCredentialsException.class, () -> service.login(login("NJO-0002", "wrong")));

        owner.setStatus(OwnerStatus.REJECTED);
        owner.setRejectionReason("PAN unclear");
        e = assertThrows(OwnerNotApprovedException.class, () -> service.login(login("NJO-0002", "emailed-pass")));
        assertEquals("Your application was rejected: PAN unclear", e.getMessage());
    }

    @Test
    @DisplayName("the first sign-in with the emailed password gets a setup token, good only for changing it")
    void firstLoginGetsSetupToken() {
        owner.setMustChangePassword(true);
        AuthResponse r = service.login(login("NJO-0002", "emailed-pass"));
        assertEquals("ROLE_OWNER_SETUP", r.getRole());
        assertEquals("ROLE_OWNER_SETUP-token", r.getToken());
        assertTrue(r.getMustChangePassword());
    }

    @Test
    @DisplayName("choosing a password clears the flag and gives a normal owner token")
    void changePasswordIssuesOwnerToken() {
        owner.setMustChangePassword(true);

        AuthResponse r = service.changePassword(9L, "emailed-pass", "my-own-password");

        assertEquals("ROLE_OWNER", r.getRole());
        assertFalse(owner.isMustChangePassword());
        assertTrue(encoder.matches("my-own-password", owner.getPassword()));
        assertThrows(BadCredentialsException.class, () -> service.changePassword(9L, "wrong", "another-password"));
        assertThrows(IllegalArgumentException.class, () -> service.changePassword(9L, "my-own-password", "short"));
    }

    @Test
    @DisplayName("the setup token stops working once the password is changed")
    void setupTokenDiesAfterPasswordChange() {
        owner.setMustChangePassword(true);
        long issuedAt = owner.getTokenVersion();
        assertTrue(versions.isCurrent("ROLE_OWNER_SETUP", "9", issuedAt));

        service.changePassword(9L, "emailed-pass", "my-own-password");

        assertFalse(versions.isCurrent("ROLE_OWNER_SETUP", "9", issuedAt));
        assertFalse(versions.isCurrent("ROLE_OWNER", "9", issuedAt));
    }
}

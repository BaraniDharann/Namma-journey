package com.travelplatform.service;

import com.travelplatform.entity.User;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A traveller who first signs in with Google has no password, so the browser has nothing to
 * remember and the email form can never work for them. They may save one once; after that the
 * normal forgot-password flow is the only way to change it, so a stolen session cannot quietly
 * swap the password out.
 */
class UserServiceSetPasswordTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private UserRepository userRepository;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new UserService(mock(TravelBookingRepository.class), mock(RoutingService.class),
                mock(DriverRepository.class), mock(OwnerService.class), userRepository, mock(OtpService.class),
                encoder, mock(NotificationService.class), mock(TripDriverPhotoRepository.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private User googleUser(String password) {
        User u = new User();
        u.setId(USER_ID);
        u.setEmail("traveller@example.com");
        u.setLoginType(User.LoginType.GOOGLE);
        u.setPassword(password);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    @DisplayName("a Google-only traveller can save a password, stored hashed")
    void savesFirstPassword() {
        User u = googleUser(null);
        service.setPassword(USER_ID, "Journey@2026");
        verify(userRepository).save(u);
        assertTrue(encoder.matches("Journey@2026", u.getPassword()));
    }

    @Test
    @DisplayName("an account that already has a password is not overwritten")
    void refusesWhenPasswordExists() {
        googleUser(encoder.encode("Existing@123"));
        assertThrows(IllegalArgumentException.class, () -> service.setPassword(USER_ID, "Journey@2026"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a password shorter than 8 characters is refused")
    void refusesShortPassword() {
        googleUser(null);
        assertThrows(IllegalArgumentException.class, () -> service.setPassword(USER_ID, "short"));
        verify(userRepository, never()).save(any());
    }
}

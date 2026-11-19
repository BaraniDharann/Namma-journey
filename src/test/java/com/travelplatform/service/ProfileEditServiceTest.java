package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Owner;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.OwnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Contact details change freely; payouts and login details need the current password. */
class ProfileEditServiceTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    private OwnerRepository owners;
    private DriverRepository drivers;
    private EmailService email;
    private ProfileEditService service;
    private Owner owner;
    private Driver driver;

    @BeforeEach
    void setUp() {
        owners = mock(OwnerRepository.class);
        drivers = mock(DriverRepository.class);
        email = mock(EmailService.class);
        service = new ProfileEditService(owners, drivers, ENCODER, email);

        owner = new Owner();
        owner.setId(5L);
        owner.setEmail("owner@example.com");
        owner.setTravelsName("Sri Travels");
        owner.setPassword(ENCODER.encode("ownerpass"));
        when(owners.findById(5L)).thenReturn(Optional.of(owner));

        driver = new Driver();
        driver.setId(7L);
        driver.setName("Barani");
        driver.setMobile("9876543210");
        driver.setEmail("driver@example.com");
        driver.setPassword(ENCODER.encode("secret123"));
        when(drivers.findById(7L)).thenReturn(Optional.of(driver));
        when(drivers.findByMobile(anyString())).thenReturn(Optional.empty());
        when(drivers.findByEmail(anyString())).thenReturn(Optional.empty());
    }

    private ProfileEditService.OwnerPayout payout(String password) {
        return new ProfileEditService.OwnerPayout(password, "Sri", "123456789012", "sbin0001234", "SBI", "sri@okaxis");
    }

    @Test
    @DisplayName("bank details change only with the right password, and the owner is emailed")
    void payoutNeedsPassword() {
        assertThrows(IllegalArgumentException.class, () -> service.updateOwnerPayout(5L, payout("wrong")));
        verify(owners, never()).save(any());
        verify(email, never()).sendOwnerPayoutDetailsChanged(any(), any());

        service.updateOwnerPayout(5L, payout("ownerpass"));
        assertEquals("123456789012", owner.getBankAccountNumber());
        assertEquals("SBIN0001234", owner.getBankIfsc());
        verify(email).sendOwnerPayoutDetailsChanged("owner@example.com", "Sri Travels");
    }

    @Test
    @DisplayName("owner contact details need no password but must be valid")
    void ownerDetails() {
        assertThrows(IllegalArgumentException.class, () -> service.updateOwnerDetails(5L,
                new ProfileEditService.OwnerDetails("Sri Travels", "Ravi", "12345")));
        service.updateOwnerDetails(5L, new ProfileEditService.OwnerDetails("Sri Tours", "Ravi", "9876500000"));
        assertEquals("Sri Tours", owner.getTravelsName());
        assertEquals("9876500000", owner.getPhone());
    }

    @Test
    @DisplayName("a driver renames freely but needs the password to change mobile or email")
    void driverLoginNeedsPassword() {
        service.updateDriverDetails(7L, new ProfileEditService.DriverDetails("Barani T", "9876543210", "driver@example.com", null));
        assertEquals("Barani T", driver.getName());

        assertThrows(IllegalArgumentException.class, () -> service.updateDriverDetails(7L,
                new ProfileEditService.DriverDetails("Barani T", "9000000000", "driver@example.com", null)));
        assertEquals("9876543210", driver.getMobile());

        service.updateDriverDetails(7L, new ProfileEditService.DriverDetails("Barani T", "9000000000", "driver@example.com", "secret123"));
        assertEquals("9000000000", driver.getMobile());
    }
}

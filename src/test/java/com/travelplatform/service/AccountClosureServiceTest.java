package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.User;
import com.travelplatform.repository.DriverLedgerRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.OwnerLedgerRepository;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.UserRepository;
import com.travelplatform.security.TokenVersionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Delete account" closes rather than erases: details are wiped and login dies, but only once
 * nothing is left open - an unfinished trip or unsettled money blocks it.
 */
class AccountClosureServiceTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    private UserRepository users;
    private DriverRepository drivers;
    private OwnerRepository owners;
    private DriverLedgerRepository driverLedger;
    private OwnerLedgerRepository ownerLedger;
    private TokenVersionService tokens;
    private TypedQuery<Long> counts;
    private AccountClosureService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        users = mock(UserRepository.class);
        drivers = mock(DriverRepository.class);
        owners = mock(OwnerRepository.class);
        driverLedger = mock(DriverLedgerRepository.class);
        ownerLedger = mock(OwnerLedgerRepository.class);
        tokens = mock(TokenVersionService.class);
        service = new AccountClosureService(users, drivers, owners, driverLedger, ownerLedger, ENCODER, tokens);

        EntityManager em = mock(EntityManager.class);
        counts = mock(TypedQuery.class, RETURNS_SELF);
        when(counts.getSingleResult()).thenReturn(0L);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(counts);
        when(em.createQuery(anyString())).thenReturn(mock(Query.class, RETURNS_SELF));
        ReflectionTestUtils.setField(service, "em", em);

        when(driverLedger.balanceOf(any())).thenReturn(BigDecimal.ZERO);
        when(ownerLedger.balanceOf(any())).thenReturn(BigDecimal.ZERO);
    }

    private Driver driver() {
        Driver d = new Driver();
        d.setId(7L);
        d.setName("Barani");
        d.setMobile("9876543210");
        d.setEmail("driver@example.com");
        d.setLicenseNumber("TN0120200001234");
        d.setPayoutUpiId("barani@okaxis");
        d.setPassword(ENCODER.encode("secret123"));
        when(drivers.findById(7L)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    @DisplayName("a driver with a clean slate is wiped, locked out and revoked")
    void closesDriver() {
        Driver d = driver();
        String oldHash = d.getPassword();

        service.closeDriver(7L, "secret123");

        assertEquals("Closed account", d.getName());
        assertEquals("closed-7", d.getMobile());
        assertEquals("closed-driver-7@closed.invalid", d.getEmail());
        assertNull(d.getPayoutUpiId());
        assertEquals(Driver.Status.INACTIVE, d.getStatus());
        assertNotNull(d.getClosedAt());
        assertTrue(!d.getPassword().equals(oldHash) && !ENCODER.matches("secret123", d.getPassword()));
        verify(tokens).revokeDriver(d);
    }

    @Test
    @DisplayName("an unfinished trip blocks closing, and nothing is wiped")
    void openTripBlocks() {
        Driver d = driver();
        when(counts.getSingleResult()).thenReturn(1L);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.closeDriver(7L, "secret123"));

        assertTrue(e.getMessage().contains("not finished"));
        assertEquals("Barani", d.getName());
        verify(tokens, never()).revokeDriver(any());
    }

    @Test
    @DisplayName("money still owed in either direction blocks closing")
    void balanceBlocks() {
        driver();
        when(driverLedger.balanceOf(7L)).thenReturn(new BigDecimal("-120.00"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.closeDriver(7L, "secret123"));
        assertTrue(e.getMessage().contains("dues"));
    }

    @Test
    @DisplayName("a wrong password closes nothing")
    void wrongPassword() {
        Driver d = driver();
        assertThrows(IllegalArgumentException.class, () -> service.closeDriver(7L, "nope"));
        assertEquals("Barani", d.getName());
    }

    @Test
    @DisplayName("the platform owner account can never be closed")
    void platformOwnerRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.closeOwner(1L, true, "anything"));
        verify(owners, never()).findById(any());
    }

    @Test
    @DisplayName("an owner's bank details are wiped and their fleet drivers closed with them")
    void closesOwnerAndFleet() {
        Owner o = new Owner();
        o.setId(5L);
        o.setTravelsName("Sri Travels");
        o.setEmail("owner@example.com");
        o.setBankAccountNumber("123456789012");
        o.setUpiId("sri@okaxis");
        o.setPassword(ENCODER.encode("ownerpass"));
        when(owners.findById(5L)).thenReturn(Optional.of(o));
        Driver d = driver();
        when(drivers.findByOwnerIdAndFleetOrderByCreatedAtDesc(5L, Driver.Fleet.OWNER_FLEET)).thenReturn(List.of(d));

        service.closeOwner(5L, false, "ownerpass");

        assertNull(o.getBankAccountNumber());
        assertNull(o.getUpiId());
        assertEquals("closed-owner-5@closed.invalid", o.getEmail());
        assertEquals("Sri Travels", o.getTravelsName()); // the business name stays on old tickets
        assertNotNull(o.getClosedAt());
        assertEquals("Closed account", d.getName());
        verify(tokens).revokeOwner(o);
        verify(tokens).revokeDriver(d);
    }

    @Test
    @DisplayName("a Google-only traveller (no password) can close with the on-screen confirmation")
    void googleTravellerCloses() {
        UUID id = UUID.randomUUID();
        User u = new User();
        u.setId(id);
        u.setName("Asha");
        u.setEmail("asha@example.com");
        u.setPhone("9123456780");
        when(users.findById(id)).thenReturn(Optional.of(u));

        service.closeUser(id, null);

        assertEquals("closed-" + id + "@closed.invalid", u.getEmail());
        assertNull(u.getPhone());
        verify(tokens).revokeUser(u);
    }
}

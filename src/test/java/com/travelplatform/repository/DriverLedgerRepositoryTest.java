package com.travelplatform.repository;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.DriverLedgerEntry;
import com.travelplatform.security.FieldEncryption;
import com.travelplatform.security.FieldEncryptionTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V21 against real Postgres: ciphertext at rest, the ledger's balance maths and its guards. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DriverLedgerRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private DriverRepository drivers;
    @Autowired
    private DriverLedgerRepository ledger;
    @Autowired
    private EntityManager em;

    private Driver driver;

    @BeforeEach
    void setUp() {
        FieldEncryption.configure(FieldEncryptionTest.KEY);
        Driver d = new Driver();
        d.setName("Ravi");
        d.setMobile("9" + (100000000 + (int) (Math.random() * 899999999)));
        d.setPassword("x");
        d.setLicenseNumber("L-" + UUID.randomUUID());
        d.setAadhaarNumber(String.valueOf(100000000000L + (long) (Math.random() * 899999999999L)));
        d.setFleet(Driver.Fleet.INDEPENDENT);
        d.setServesTrip(false);
        d.setVehicleType(com.travelplatform.entity.VehicleType.AUTO);
        d.setCreatedAt(LocalDateTime.now());
        d.setPayoutUpiId("ravi@okaxis");
        driver = drivers.saveAndFlush(d);
        em.clear();
    }

    @AfterEach
    void noKey() {
        FieldEncryption.configure(null);
    }

    private DriverLedgerEntry entry(DriverLedgerEntry.Type type, String amount, UUID booking, String ref) {
        DriverLedgerEntry e = new DriverLedgerEntry();
        e.setDriverId(driver.getId());
        e.setType(type);
        e.setAmount(new BigDecimal(amount));
        e.setBookingId(booking);
        e.setReference(ref);
        e.setCreatedBy("test");
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }

    @Test
    @DisplayName("the payout UPI ID is ciphertext in the table and plain text in the entity")
    void upiEncryptedAtRest() {
        Object stored = em.createNativeQuery("SELECT payout_upi_id FROM drivers WHERE id = :id")
                .setParameter("id", driver.getId()).getSingleResult();
        assertTrue(stored.toString().startsWith("v1:"));
        assertTrue(!stored.toString().contains("ravi"));
        assertEquals("ravi@okaxis", drivers.findById(driver.getId()).orElseThrow().getPayoutUpiId());
    }

    @Test
    @DisplayName("balance is the exact sum; payouts serialise on the driver's lock")
    void balance() {
        ledger.save(entry(DriverLedgerEntry.Type.RIDE_EARNING, "210.00", UUID.randomUUID(), null));
        ledger.save(entry(DriverLedgerEntry.Type.CASH_COMMISSION, "-12.50", UUID.randomUUID(), null));
        ledger.save(entry(DriverLedgerEntry.Type.PAYOUT, "-100.00", null, "UTR123456"));
        ledger.flush();

        ledger.lockDriver(driver.getId());
        assertEquals(new BigDecimal("97.50"), ledger.balanceOf(driver.getId()));
        assertEquals(1, ledger.balances().size());
    }

    @Test
    @DisplayName("one earning per ride, and every transfer carries its UTR")
    void guards() {
        UUID ride = UUID.randomUUID();
        ledger.saveAndFlush(entry(DriverLedgerEntry.Type.RIDE_EARNING, "210.00", ride, null));
        assertThrows(DataIntegrityViolationException.class,
                () -> ledger.saveAndFlush(entry(DriverLedgerEntry.Type.RIDE_EARNING, "210.00", ride, null)));
    }

    @Test
    @DisplayName("a payout without a reference is refused by the database itself")
    void transferNeedsReference() {
        assertThrows(DataIntegrityViolationException.class,
                () -> ledger.saveAndFlush(entry(DriverLedgerEntry.Type.PAYOUT, "-50.00", null, null)));
    }
}

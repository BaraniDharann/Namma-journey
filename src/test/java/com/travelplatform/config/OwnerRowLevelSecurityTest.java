package com.travelplatform.config;

import com.travelplatform.entity.CarCategory;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.Vehicle;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Postgres row-level security (V28) keeps each owner to their own rows, whatever the query: a list,
 * a lookup by id, or hand-written SQL. The scope lives on the connection only while an owner's
 * request uses it, and is gone when the connection goes back to the pool.
 *
 * <p>Not transactional: each repository call takes its own pooled connection, as a request does.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(OwnerScopeDataSourcePostProcessor.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OwnerRowLevelSecurityTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // One connection: the scoped one and the next one are provably the same pooled connection.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
        // Flyway gets its own connection, so it does not compete for the single pooled one.
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private TravelBookingRepository bookings;
    @Autowired
    private VehicleRepository vehicles;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;

    private long ownerA;
    private long ownerB;
    private UUID tripA;
    private UUID tripB;
    private UUID cityRide;

    @BeforeEach
    void fixtures() {
        OwnerScope.clear();
        jdbc.update("DELETE FROM travel_bookings");
        jdbc.update("DELETE FROM vehicles");
        jdbc.update("DELETE FROM owners");
        ownerA = jdbc.queryForObject("INSERT INTO owners (email, role, created_at, owner_code, status) "
                + "VALUES ('a@x.com', 'ROLE_OWNER', now(), 'NJO-0001', 'APPROVED') RETURNING id", Long.class);
        ownerB = jdbc.queryForObject("INSERT INTO owners (email, role, created_at, owner_code, status) "
                + "VALUES ('b@x.com', 'ROLE_OWNER', now(), 'NJO-0002', 'APPROVED') RETURNING id", Long.class);
        tripA = bookings.save(booking(ownerA, TravelBooking.ServiceMode.TRIP)).getId();
        tripB = bookings.save(booking(ownerB, TravelBooking.ServiceMode.TRIP)).getId();
        cityRide = bookings.save(booking(null, TravelBooking.ServiceMode.CITY)).getId();
    }

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    private static TravelBooking booking(Long owner, TravelBooking.ServiceMode mode) {
        TravelBooking b = new TravelBooking();
        b.setUserId(UUID.randomUUID());
        b.setUserName("T");
        b.setUserPhone("9876543210");
        b.setFromPlace("A");
        b.setToPlace("B");
        b.setFromDate(LocalDate.now());
        b.setToDate(LocalDate.now());
        b.setTravelDays(1);
        b.setTravelMembers(2);
        b.setAcType("AC");
        b.setDistanceKm(100.0);
        b.setEstimatedTimeMinutes(120L);
        b.setBookingDate(LocalDateTime.now());
        b.setTotalAmount(new BigDecimal("1000.00"));
        b.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        b.setServiceMode(mode);
        b.setOwnerId(owner);
        if (mode == TravelBooking.ServiceMode.TRIP) {
            b.setCarCategory(CarCategory.SEDAN);
        }
        return b;
    }

    @Test
    @DisplayName("an owner sees only their own rows, by list, by id and through hand-written SQL")
    void ownerSeesOnlyOwnRows() {
        OwnerScope.bind(ownerB, false);

        List<UUID> visible = bookings.findAll().stream().map(TravelBooking::getId).toList();
        assertEquals(List.of(tripB), visible);
        assertTrue(bookings.findById(tripA).isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM travel_bookings", Integer.class));
    }

    @Test
    @DisplayName("the platform owner also sees platform rows (City rides), but not other owners' rows")
    void platformOwnerAlsoSeesUnownedRows() {
        OwnerScope.bind(ownerA, true);

        List<UUID> visible = bookings.findAll().stream().map(TravelBooking::getId).sorted().toList();
        assertEquals(List.of(tripA, cityRide).stream().sorted().toList(), visible);
        assertTrue(bookings.findById(tripB).isEmpty());
    }

    @Test
    @DisplayName("a scoped connection with no owner set sees nothing")
    void scopeWithoutSettingSeesNothing() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SET ROLE nj_owner_scope");
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM travel_bookings")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
            s.execute("RESET ROLE");
        }
    }

    @Test
    @DisplayName("once an owner's request is done, the same pooled connection sees everything again")
    void connectionIsResetAfterScopedUse() {
        OwnerScope.bind(ownerB, false);
        assertEquals(1, bookings.findAll().size());
        OwnerScope.clear();

        assertEquals(3, bookings.findAll().size());
        assertNotEquals("nj_owner_scope", jdbc.queryForObject("SELECT current_user", String.class));
        assertEquals("", jdbc.queryForObject("SELECT COALESCE(current_setting('nj.owner_id', true), '')", String.class));
    }

    @Test
    @DisplayName("an owner cannot write a row for another owner")
    void ownerCannotInsertForAnotherOwner() {
        OwnerScope.bind(ownerB, false);
        Vehicle v = new Vehicle();
        v.setOwnerId(ownerA);
        v.setCategory(CarCategory.SEDAN);
        v.setRegistrationNumber("KA01ZZ9999");
        v.setSeats(4);

        assertThrows(RuntimeException.class, () -> vehicles.save(v));
        OwnerScope.clear();
        assertEquals(0, vehicles.count());
    }
}

package com.travelplatform.service;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.config.OwnerScopeDataSourcePostProcessor;
import com.travelplatform.entity.CarCategory;
import com.travelplatform.entity.Vehicle;
import com.travelplatform.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Duplicate checks see every owner's rows although row-level security (V28) hides them from the
 * owner's own connection - even when the owner's transaction already holds a scoped connection.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({OwnerScopeDataSourcePostProcessor.class, UnscopedLookups.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UnscopedLookupsTest {

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
    private VehicleRepository vehicles;
    @Autowired
    private com.travelplatform.repository.BusRepository buses;
    @Autowired
    private UnscopedLookups lookups;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    @Test
    @DisplayName("an owner adding a plate another owner already has is told so")
    void plateOfAnotherOwnerIsSeen() {
        Long a = jdbc.queryForObject("INSERT INTO owners (email, role, created_at) VALUES ('a@x.com', 'ROLE_OWNER', now()) RETURNING id", Long.class);
        Long b = jdbc.queryForObject("INSERT INTO owners (email, role, created_at) VALUES ('b@x.com', 'ROLE_OWNER', now()) RETURNING id", Long.class);
        Vehicle theirs = new Vehicle();
        theirs.setOwnerId(a);
        theirs.setCategory(CarCategory.SEDAN);
        theirs.setRegistrationNumber("KA01AA0001");
        theirs.setSeats(4);
        vehicles.save(theirs);
        OwnerScope.bind(b, false);

        Boolean taken = new TransactionTemplate(transactions).execute(tx -> {
            assertTrue(vehicles.findByRegistrationKey("KA01AA0001").isEmpty(), "the row filter hides it from owner B");
            return lookups.vehiclePlateTaken("KA01AA0001");
        });

        assertEquals(Boolean.TRUE, taken);
        assertTrue(OwnerScope.current().isPresent(), "the owner's scope is back afterwards");
    }

    @Test
    @DisplayName("an owner adding a bus whose plate another owner's bus has is told so")
    void busPlateOfAnotherOwnerIsSeen() {
        Long a = jdbc.queryForObject("INSERT INTO owners (email, role, created_at) VALUES ('ba@x.com', 'ROLE_OWNER', now()) RETURNING id", Long.class);
        Long b = jdbc.queryForObject("INSERT INTO owners (email, role, created_at) VALUES ('bb@x.com', 'ROLE_OWNER', now()) RETURNING id", Long.class);
        Long theirs = jdbc.queryForObject("INSERT INTO buses (owner_id, registration_number, name, permit_type) "
                + "VALUES (?, 'TN01BU0001', 'Their bus', 'STAGE_CARRIAGE') RETURNING id", Long.class, a);
        OwnerScope.bind(b, false);

        java.util.Optional<Long> holder = new TransactionTemplate(transactions).execute(tx -> {
            assertTrue(buses.findByRegistrationKey("TN01BU0001").isEmpty(), "the row filter hides it from owner B");
            return lookups.busPlateHolder("TN01BU0001");
        });

        assertEquals(java.util.Optional.of(theirs), holder);
        assertTrue(OwnerScope.current().isPresent(), "the owner's scope is back afterwards");
    }
}

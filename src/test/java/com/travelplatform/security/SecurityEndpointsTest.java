package com.travelplatform.security;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.User;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole application, booted against real Postgres, answering real HTTP requests through the
 * real security filter chain. Proves the access rules end to end - and that every bean added by
 * the hardening work wires up at startup.
 */
@SpringBootTest
@AutoConfigureMockMvc
// Spring Boot disables metric exporters in tests unless asked; production exposes /actuator/prometheus.
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@Testcontainers(disabledWithoutDocker = true)
class SecurityEndpointsTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("DB_URL", POSTGRES::getJdbcUrl);
        r.add("DB_USERNAME", POSTGRES::getUsername);
        r.add("DB_PASSWORD", POSTGRES::getPassword);
        r.add("JWT_SECRET", () -> "endpoint-test-secret-0123456789abcdef0123");
        r.add("JWT_EXPIRATION", () -> "3600000");
        r.add("GOOGLE_CLIENT_ID", () -> "test-client");
        r.add("GOOGLE_CLIENT_SECRET", () -> "test-secret");
        r.add("MAIL_USERNAME", () -> "x");
        r.add("MAIL_PASSWORD", () -> "x");
        r.add("OWNER_UPI_ID", () -> "owner@bank");
        r.add("APP_CACHE_TYPE", () -> "caffeine");
        r.add("TELEGRAM_ENABLED", () -> "false");
        r.add("METRICS_SCRAPE_TOKEN", () -> "scrape-token-for-tests");
        r.add("spring.mail.host", () -> "127.0.0.1");
        r.add("spring.mail.port", () -> "9");
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwt;
    @Autowired
    private UserRepository users;
    @Autowired
    private OwnerRepository owners;

    private User alice;
    private User bob;
    private String aliceToken;
    private String ownerToken;

    @BeforeEach
    void accounts() {
        alice = users.save(user("alice"));
        bob = users.save(user("bob"));
        aliceToken = "Bearer " + jwt.generateToken(alice.getId().toString(), "ROLE_USER", alice.getTokenVersion());

        Owner owner = new Owner();
        owner.setEmail("owner-" + UUID.randomUUID() + "@x.com");
        owner.setPassword("x");
        owner.setRole("ROLE_OWNER");
        owner = owners.save(owner);
        ownerToken = "Bearer " + jwt.generateToken(owner.getId().toString(), "ROLE_OWNER", owner.getTokenVersion());
    }

    private static User user(String name) {
        User u = new User();
        u.setName(name);
        u.setEmail(name + "-" + UUID.randomUUID() + "@x.com");
        u.setLoginType(User.LoginType.EMAIL);
        u.setRole("ROLE_USER");
        u.setCreatedAt(LocalDateTime.now());
        return u;
    }

    @Test
    @DisplayName("actuator metrics and caches are for the owner only - not for any signed-in passenger")
    void actuatorIsOwnerOnly() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").header("Authorization", aliceToken)).andExpect(status().isForbidden());
        mvc.perform(delete("/actuator/caches").header("Authorization", aliceToken)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").header("Authorization", ownerToken)).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Prometheus reads metrics with its scrape token only")
    void prometheusNeedsTheScrapeToken() throws Exception {
        mvc.perform(get("/actuator/prometheus").header("Authorization", aliceToken)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer scrape-token-for-tests"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a passenger cannot read another passenger's package bookings")
    void packageBookingsAreScopedToTheirOwner() throws Exception {
        mvc.perform(get("/api/user/" + bob.getId() + "/package-bookings").header("Authorization", aliceToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/user/" + alice.getId() + "/package-bookings").header("Authorization", aliceToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("after logout the same token is refused everywhere")
    void logoutRevokesTheToken() throws Exception {
        mvc.perform(get("/api/user/" + alice.getId() + "/bookings").header("Authorization", aliceToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", aliceToken)).andExpect(status().isNoContent());
        mvc.perform(get("/api/user/" + alice.getId() + "/bookings").header("Authorization", aliceToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unknown URL is a 404, not a 500 - scanners must not trip the error-rate alert")
    void unknownPathsAre404() throws Exception {
        mvc.perform(get("/api/public/no-such-thing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/no-such-endpoint").header("Authorization", aliceToken)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("logging out on one device ends that session only - the other device stays signed in")
    void logoutEndsOnlyThatSession() throws Exception {
        String phone = "Bearer " + jwt.generateToken(alice.getId().toString(), "ROLE_USER", alice.getTokenVersion());
        String laptop = "Bearer " + jwt.generateToken(alice.getId().toString(), "ROLE_USER", alice.getTokenVersion());

        mvc.perform(post("/api/auth/logout").header("Authorization", phone)).andExpect(status().isNoContent());

        mvc.perform(get("/api/user/" + alice.getId() + "/bookings").header("Authorization", phone))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/user/" + alice.getId() + "/bookings").header("Authorization", laptop))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("identity documents are not served without a signed link")
    void identityDocumentsNeedASignature() throws Exception {
        mvc.perform(get("/uploads/drivers/aadhaar_" + UUID.randomUUID() + ".jpg")).andExpect(status().isNotFound());
    }
}

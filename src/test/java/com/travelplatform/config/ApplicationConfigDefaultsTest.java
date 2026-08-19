package com.travelplatform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the settings in {@code application.yml} that are load-bearing for a safe deployment.
 *
 * <p>Every assertion here corresponds to a setting that is silently catastrophic when wrong: the
 * application still starts, tests still pass, and the damage shows up in production. A secret
 * that acquires a fallback value means every deployment that forgot to configure it shares one
 * credential. {@code ddl-auto} back on {@code update} means Hibernate quietly rewrites live
 * tables. {@code otp.test.mode} left on means every account on the internet is takeable.
 *
 * <p>Parsing the file also catches YAML that no longer loads, which unit tests built on plain
 * mocks would otherwise never notice.
 */
class ApplicationConfigDefaultsTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertNotNull(in, "application.yml is missing from the classpath");
            Map<String, Object> root = new Yaml().load(in);
            assertNotNull(root, "application.yml parsed to nothing");
            return root;
        } catch (Exception e) {
            throw new AssertionError("application.yml does not parse: " + e.getMessage(), e);
        }
    }

    /** Walks a dotted path, e.g. {@code spring.jpa.hibernate.ddl-auto}. */
    @SuppressWarnings("unchecked")
    private String at(Map<String, Object> root, String path) {
        Object node = root;
        for (String segment : path.split("\\.")) {
            if (!(node instanceof Map)) {
                return null;
            }
            node = ((Map<String, Object>) node).get(segment);
        }
        return node == null ? null : node.toString();
    }

    @Test
    @DisplayName("application.yml parses")
    void configParses() {
        assertFalse(load().isEmpty());
    }

    @Test
    @DisplayName("no secret carries a fallback value")
    void secretsHaveNoDefaults() {
        Map<String, Object> root = load();

        // "${VAR}" is required; "${VAR:something}" silently substitutes when unset.
        for (String path : new String[]{
                "jwt.secret",
                "spring.datasource.url",
                "spring.datasource.username",
                "spring.datasource.password",
                "payment.upi.id"}) {
            String value = at(root, path);
            assertNotNull(value, path + " is missing from application.yml");
            assertTrue(value.startsWith("${") && value.endsWith("}"),
                    path + " must be environment-driven, was: " + value);
            assertFalse(value.contains(":"),
                    path + " must not have a fallback — a deployment that forgets to set it would "
                            + "silently run on a value baked into the source. Was: " + value);
        }
    }

    @Test
    @DisplayName("Flyway owns the schema and Hibernate only validates it")
    void hibernateNeverAltersTheSchema() {
        Map<String, Object> root = load();
        assertEquals("${JPA_DDL_AUTO:validate}", at(root, "spring.jpa.hibernate.ddl-auto"),
                "ddl-auto must default to validate; 'update' lets Hibernate rewrite live tables");
        assertEquals("true", at(root, "spring.flyway.enabled"));
    }

    @Test
    @DisplayName("OTP test mode is off by default")
    void otpTestModeIsOffByDefault() {
        // When on, OTPs are written to the database instead of emailed, so anyone who can read
        // the otps table can take over any account.
        assertEquals("${OTP_TEST_MODE:false}", at(load(), "otp.test.mode"));
    }

    @Test
    @DisplayName("the owner bootstrap secret is empty by default, keeping create-admin disabled")
    void ownerBootstrapIsDisabledByDefault() {
        String value = at(load(), "app.bootstrap.owner-secret");
        assertEquals("${OWNER_BOOTSTRAP_SECRET:}", value,
                "create-admin mints owner tokens; it must stay disabled unless explicitly enabled");
    }

    @Test
    @DisplayName("forwarded headers are trusted from nobody by default")
    void trustedProxiesIsEmptyByDefault() {
        // Trusting X-Forwarded-For from any peer lets a caller mint a new identity per request
        // and walk straight through every rate limit.
        assertEquals("${RATELIMIT_TRUSTED_PROXIES:}", at(load(), "app.ratelimit.trusted-proxies"));
    }

    @Test
    @DisplayName("actuator exposes no write or introspection endpoints beyond health and info")
    void actuatorExposureIsNarrow() {
        String include = at(load(), "management.endpoints.web.exposure.include");
        assertNotNull(include);
        for (String forbidden : new String[]{"env", "beans", "configprops", "heapdump", "threaddump",
                "loggers", "shutdown", "mappings"}) {
            assertFalse(java.util.Arrays.asList(include.split(",")).contains(forbidden),
                    "actuator must not expose '" + forbidden + "'; include was: " + include);
        }
    }

    @Test
    @DisplayName("shutdown drains in-flight requests")
    void shutdownIsGraceful() {
        Map<String, Object> root = load();
        assertEquals("graceful", at(root, "server.shutdown"),
                "an immediate shutdown severs in-flight bookings and payments on every deploy");
        assertNotNull(at(root, "spring.lifecycle.timeout-per-shutdown-phase"),
                "a graceful shutdown with no timeout can hang past the orchestrator's SIGKILL");
    }
}

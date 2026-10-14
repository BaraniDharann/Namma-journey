package com.travelplatform.service;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.DriverLoginRequest;
import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Driver.status is the driver's own online/offline switch (dashboard "go offline"). Login refused
 * INACTIVE drivers, so a driver who went offline for the night could never get back in to go
 * online again.
 */
class DriverLoginAvailabilityTest {

    @Test
    @DisplayName("a driver who went offline can still log in (and then go back online)")
    void offlineDriverCanLogIn() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        DriverRepository drivers = mock(DriverRepository.class);
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.generateToken(any(), any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn("token");
        DriverAuthService service = new DriverAuthService();
        ReflectionTestUtils.setField(service, "driverRepository", drivers);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "jwtUtil", jwt);

        Driver offline = new Driver();
        offline.setId(7L);
        offline.setRole("ROLE_DRIVER");
        offline.setPassword(encoder.encode("Secret#2026"));
        offline.setFirstLogin(false);
        offline.setStatus(Driver.Status.INACTIVE);
        when(drivers.findByMobile("9000000001")).thenReturn(Optional.of(offline));

        DriverLoginRequest req = new DriverLoginRequest();
        req.setMobile("9000000001");
        req.setPassword("Secret#2026");

        Map<String, Object> response = service.login(req);
        assertEquals("token", response.get("token"));
    }
}

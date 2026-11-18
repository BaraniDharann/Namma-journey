package com.travelplatform.service.dispatch;

import com.travelplatform.config.DispatchProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class DriverQualityServiceTest {

    private final DispatchProperties props = new DispatchProperties();
    private final DriverQualityService service = new DriverQualityService(mock(NamedParameterJdbcTemplate.class), props);

    @Test
    @DisplayName("a reliable, well-rated driver costs nothing extra")
    void reliableDriver() {
        assertEquals(0, service.penalty(new DriverQualityService.Quality(20, 0, 4.8)));
    }

    @Test
    @DisplayName("accepting half of their offers costs half the accept penalty")
    void halfAcceptance() {
        assertEquals(60, service.penalty(new DriverQualityService.Quality(5, 5, null)));
    }

    @Test
    @DisplayName("too few offers to judge: acceptance is ignored")
    void newDriverNotPunished() {
        assertEquals(0, service.penalty(new DriverQualityService.Quality(0, 4, null)));
    }

    @Test
    @DisplayName("each star below 4.5 costs a minute")
    void lowRating() {
        assertEquals(90, service.penalty(new DriverQualityService.Quality(10, 0, 3.0)));
    }

    @Test
    @DisplayName("a failed read scores everyone on ETA alone")
    void readFailureIsZero() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        doThrow(new RuntimeException("db down")).when(jdbc)
                .query(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class),
                        any(org.springframework.jdbc.core.RowCallbackHandler.class));
        DriverQualityService failing = new DriverQualityService(jdbc, props);
        assertEquals(Map.of(1L, 0, 2L, 0), failing.penaltySeconds(List.of(1L, 2L)));
    }
}

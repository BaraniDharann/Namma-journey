package com.travelplatform.service;

import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.NotificationRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Regressions found by the owner-fleet branch review. */
class OwnerFleetReviewFixesTest {

    private static Method method(Class<?> type, String name, Class<?>... params) throws NoSuchMethodException {
        return type.getMethod(name, params);
    }

    private static Set<String> evicted(Method m) {
        Caching caching = m.getAnnotation(Caching.class);
        return caching == null ? Set.of() : Arrays.stream(caching.evict()).flatMap(e -> Arrays.stream(e.value()))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("verifying a payment is one transaction and refreshes every revenue figure")
    void verifyPaymentIsTransactionalAndEvictsRevenue() throws Exception {
        Method verify = method(PaymentService.class, "verifyPayment", UUID.class, String.class);
        assertNotNull(verify.getAnnotation(Transactional.class));
        assertTrue(evicted(verify).containsAll(Set.of("dailyRevenue", "monthlyRevenue", "yearlyRevenue", "monthlyRevenueSeries")),
                evicted(verify).toString());
    }

    @Test
    @DisplayName("editing or deleting a driver drops every owner's cached copy (keys carry the owner)")
    void driverEvictionsCoverEveryOwnersKey() throws Exception {
        for (Method m : new Method[]{
                method(AdminDriverService.class, "deleteDriver", Long.class),
                method(AdminDriverService.class, "updateVehicle", Long.class, com.travelplatform.dto.VehicleRequest.class)}) {
            Caching caching = m.getAnnotation(Caching.class);
            CacheEvict byId = Arrays.stream(caching.evict()).filter(e -> Arrays.asList(e.value()).contains("driverById"))
                    .findFirst().orElseThrow();
            assertTrue(byId.allEntries(), m.getName());
        }
    }

    @Test
    @DisplayName("without a platform owner yet, City-ride notices still go out instead of failing the booking")
    void noPlatformOwnerDoesNotBreakNotifications() {
        NotificationRepository repo = mock(NotificationRepository.class);
        NotificationService service = new NotificationService(repo, mock(EmailService.class), mock(UserRepository.class),
                mock(TelegramDispatchService.class), new AfterCommitDispatcher(Runnable::run));
        TripRateService rates = mock(TripRateService.class);
        when(rates.platformOwnerId()).thenThrow(new IllegalStateException("no platform owner"));
        ReflectionTestUtils.setField(service, "tripRates", rates);

        assertEquals("owner", service.platformFeed());
        TravelBooking city = new TravelBooking();
        city.setId(UUID.randomUUID());
        city.setUserId(UUID.randomUUID());
        city.setServiceMode(TravelBooking.ServiceMode.CITY);
        city.setFromPlace("A");
        city.setToPlace("B");
        assertDoesNotThrow(() -> service.notifyBookingCreated(city));
    }
}

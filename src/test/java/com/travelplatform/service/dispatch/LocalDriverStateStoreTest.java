package com.travelplatform.service.dispatch;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.VehicleType;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The in-memory driver state store - what a round reads instead of the database. Postgres stays
 * the truth: a card nobody has asked for yet is loaded from it, and reconcile pulls any drift back.
 */
class LocalDriverStateStoreTest {

    private final Map<Long, Driver> table = new ConcurrentHashMap<>();
    private final Set<Long> busyInDatabase = ConcurrentHashMap.newKeySet();
    private final Set<Long> offeredInDatabase = ConcurrentHashMap.newKeySet();
    private DriverStateStore store;

    @BeforeEach
    void setUp() {
        DriverRepository drivers = mock(DriverRepository.class);
        TravelBookingRepository bookings = mock(TravelBookingRepository.class);
        when(drivers.findAllById(any())).thenAnswer(i -> {
            List<Driver> found = new ArrayList<>();
            for (Long id : i.<Iterable<Long>>getArgument(0)) {
                if (table.containsKey(id)) {
                    found.add(table.get(id));
                }
            }
            return found;
        });
        when(bookings.findBusyDriverIds(anyCollection(), any(LocalDate.class)))
                .thenAnswer(i -> i.<Collection<Long>>getArgument(0).stream().filter(busyInDatabase::contains).toList());
        when(bookings.findOfferedDriverIds(anyCollection()))
                .thenAnswer(i -> i.<Collection<Long>>getArgument(0).stream().filter(offeredInDatabase::contains).toList());
        store = new LocalDriverStateStore(new DriverCardLoader(drivers, bookings));

        table.put(1L, driver(1L, VehicleType.CAR, 4));
        table.put(2L, driver(2L, VehicleType.AUTO, 3));
    }

    private static Driver driver(long id, VehicleType vehicle, int seats) {
        Driver d = new Driver();
        d.setId(id);
        d.setVehicleType(vehicle);
        d.setVehicleSeats(seats);
        d.setServesCity(true);
        d.setStatus(Driver.Status.ACTIVE);
        d.setVerificationStatus(Driver.Verification.APPROVED);
        return d;
    }

    @Test
    @DisplayName("a card nobody has read yet is loaded from the database, state included")
    void missingCardLoadedFromDatabase() {
        busyInDatabase.add(2L);
        table.get(2L).setVerificationStatus(Driver.Verification.UNDER_REVIEW);

        Map<Long, DriverCard> cards = store.cards(List.of(1L, 2L, 99L));

        assertEquals(new DriverCard(1L, DriverState.IDLE, VehicleType.CAR, 4, true, true), cards.get(1L));
        DriverCard auto = cards.get(2L);
        assertEquals(DriverState.BUSY, auto.state());
        assertEquals(VehicleType.AUTO, auto.vehicle());
        assertFalse(auto.active(), "an unapproved driver is never offered a ride");
        assertFalse(cards.containsKey(99L), "a driver who does not exist has no card");
    }

    @Test
    @DisplayName("compare-and-set moves the state only from the expected one")
    void compareAndSetOnlyFromExpected() {
        store.cards(List.of(1L));

        assertTrue(store.compareAndSetState(1L, DriverState.IDLE, DriverState.OFFERED));
        assertFalse(store.compareAndSetState(1L, DriverState.IDLE, DriverState.OFFERED),
                "a driver already offered one ride is not offered a second");
        assertEquals(DriverState.OFFERED, store.cards(List.of(1L)).get(1L).state());
        assertTrue(store.compareAndSetState(1L, DriverState.OFFERED, DriverState.BUSY));
        assertEquals(DriverState.BUSY, store.cards(List.of(1L)).get(1L).state());
    }

    @Test
    @DisplayName("a profile change (vehicle, switches, online) keeps the driver's current state")
    void putProfileKeepsState() {
        store.cards(List.of(1L));
        store.setState(1L, DriverState.OFFERED);

        Driver changed = driver(1L, VehicleType.BIKE, 1);
        changed.setServesCity(false);
        store.putProfile(changed);

        DriverCard card = store.cards(List.of(1L)).get(1L);
        assertEquals(DriverState.OFFERED, card.state());
        assertEquals(VehicleType.BIKE, card.vehicle());
        assertEquals(1, card.seats());
        assertFalse(card.servesCity());
    }

    @Test
    @DisplayName("reconcile pulls a drifted state back to what the database says")
    void reconcileFixesDrift() {
        store.cards(List.of(1L));
        store.setState(1L, DriverState.IDLE);
        busyInDatabase.add(1L); // a CONFIRMED ride the store never heard about

        store.reconcile();

        assertEquals(DriverState.BUSY, store.cards(List.of(1L)).get(1L).state());
    }

    @Test
    @DisplayName("an offered booking in the database reads as OFFERED")
    void offeredBookingGivesOffered() {
        offeredInDatabase.add(1L);

        assertEquals(DriverState.OFFERED, store.cards(List.of(1L)).get(1L).state());
    }
}

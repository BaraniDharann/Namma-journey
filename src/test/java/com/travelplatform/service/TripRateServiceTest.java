package com.travelplatform.service;

import com.travelplatform.entity.CarCategory;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerTripRate;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.OwnerTripRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Each owner prices Trips per car category; a category they have not priced cannot be booked from them. */
class TripRateServiceTest {

    private OwnerTripRateRepository rates;
    private OwnerRepository owners;
    private TripRateService service;

    @BeforeEach
    void setUp() {
        rates = mock(OwnerTripRateRepository.class);
        owners = mock(OwnerRepository.class);
        service = new TripRateService(rates, owners, "NJO-0001");
        when(rates.save(any(OwnerTripRate.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static OwnerTripRate rate(long owner, CarCategory c, String km, String hour) {
        OwnerTripRate r = new OwnerTripRate();
        r.setOwnerId(owner);
        r.setCategory(c);
        r.setPerKm(new BigDecimal(km));
        r.setPerHour(new BigDecimal(hour));
        return r;
    }

    @Test
    @DisplayName("an owner's rate for a category is what that category costs")
    void rateForCategory() {
        when(rates.findById(new OwnerTripRate.Key(7L, CarCategory.SUV)))
                .thenReturn(Optional.of(rate(7, CarCategory.SUV, "18.00", "250.00")));

        OwnerTripRate r = service.rate(7L, CarCategory.SUV);

        assertEquals(new BigDecimal("18.00"), r.getPerKm());
    }

    @Test
    @DisplayName("a category the owner has not priced cannot be booked")
    void unpricedCategoryRefused() {
        when(rates.findById(any())).thenReturn(Optional.empty());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.rate(7L, CarCategory.TEMPO_TRAVELLER));
        assertEquals("This car type is not available", e.getMessage());
    }

    @Test
    @DisplayName("setting a rate writes both prices for that owner and category")
    void setWritesRow() {
        when(rates.findById(any())).thenReturn(Optional.empty());

        service.set(7L, CarCategory.SEDAN, new BigDecimal("12.5"), new BigDecimal("180"));

        ArgumentCaptor<OwnerTripRate> saved = ArgumentCaptor.forClass(OwnerTripRate.class);
        verify(rates).save(saved.capture());
        assertEquals(7L, saved.getValue().getOwnerId());
        assertEquals(CarCategory.SEDAN, saved.getValue().getCategory());
        assertEquals(new BigDecimal("12.50"), saved.getValue().getPerKm());
        assertEquals(new BigDecimal("180.00"), saved.getValue().getPerHour());
    }

    @Test
    @DisplayName("zero or negative prices are refused")
    void setRejectsNonPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> service.set(7L, CarCategory.SEDAN, BigDecimal.ZERO, new BigDecimal("180")));
        assertThrows(IllegalArgumentException.class,
                () -> service.set(7L, CarCategory.SEDAN, new BigDecimal("10"), new BigDecimal("-1")));
        verify(rates, never()).save(any());
    }

    @Test
    @DisplayName("the platform owner is found by the platform Owner ID")
    void platformOwnerId() {
        Owner o = new Owner();
        o.setId(38L);
        when(owners.findByOwnerCode("NJO-0001")).thenReturn(Optional.of(o));

        assertEquals(38L, service.platformOwnerId());
    }

    @Test
    @DisplayName("the rates list shows each priced category")
    void ratesFor() {
        when(rates.findByOwnerIdOrderByCategoryAsc(7L)).thenReturn(List.of(rate(7, CarCategory.SEDAN, "12", "180")));

        assertEquals(List.of(new TripRateView(CarCategory.SEDAN, new BigDecimal("12"), new BigDecimal("180"))),
                service.ratesFor(7L));
    }
}

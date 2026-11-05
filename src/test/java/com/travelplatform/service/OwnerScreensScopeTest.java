package com.travelplatform.service;

import com.travelplatform.config.BookingAccessGuard;
import com.travelplatform.config.OwnerScope;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.PackageBooking;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.Review;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TravelPackage;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.NotificationRepository;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.PackageBookingRepository;
import com.travelplatform.repository.PaymentRepository;
import com.travelplatform.repository.ReviewRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TravelPackageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every owner screen shows only the calling owner's data (V27): their payments, reviews, packages
 * and Trips. City rides (no owner) belong to the platform owner alone.
 */
class OwnerScreensScopeTest {

    private static final long ME = 7L;
    private static final long OTHER = 8L;

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    private static Payment payment(Long owner) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setBookingId(UUID.randomUUID());
        p.setUserId(UUID.randomUUID());
        p.setAmount(new BigDecimal("100.00"));
        p.setPaymentMethod(Payment.PaymentMethod.UPI);
        p.setStatus(Payment.PaymentStatus.PENDING);
        ReflectionTestUtils.setField(p, "ownerId", owner);
        return p;
    }

    private static PaymentService payments(PaymentRepository repo, TravelBookingRepository bookings) {
        PaymentService s = new PaymentService(repo, bookings, mock(DriverRepository.class), mock(NotificationService.class),
                mock(LocationTrackingService.class));
        ReflectionTestUtils.setField(s, "ownerUpiId", "platform@bank");
        ReflectionTestUtils.setField(s, "ownerName", "Namma Journey");
        return s;
    }

    @Test
    @DisplayName("pending payments are my Trips' only; no owner means the platform's City rides")
    void pendingPaymentsOnlyOwn() {
        PaymentRepository repo = mock(PaymentRepository.class);
        Payment mine = payment(ME);
        Payment theirs = payment(OTHER);
        Payment city = payment(null);
        when(repo.findByStatus(Payment.PaymentStatus.PENDING)).thenReturn(List.of(mine, theirs, city));
        PaymentService service = payments(repo, mock(TravelBookingRepository.class));

        assertEquals(List.of(mine.getId()), service.getPendingPayments(ME).stream().map(r -> r.getPaymentId()).toList());
        assertEquals(List.of(city.getId()), service.getPendingPayments(null).stream().map(r -> r.getPaymentId()).toList());
    }

    @Test
    @DisplayName("another owner's payment is not found; a City ride's only for the platform")
    void verifyOtherOwnersPayment404() {
        PaymentRepository repo = mock(PaymentRepository.class);
        Payment theirs = payment(OTHER);
        Payment city = payment(null);
        when(repo.findById(theirs.getId())).thenReturn(Optional.of(theirs));
        when(repo.findById(city.getId())).thenReturn(Optional.of(city));
        PaymentService service = payments(repo, mock(TravelBookingRepository.class));

        assertThrows(ResourceNotFoundException.class, () -> service.requireVisibleTo(theirs.getId(), ME, true));
        assertThrows(ResourceNotFoundException.class, () -> service.requireVisibleTo(city.getId(), ME, false));
        assertDoesNotThrow(() -> service.requireVisibleTo(city.getId(), ME, true));
        assertDoesNotThrow(() -> service.requireVisibleTo(theirs.getId(), OTHER, false));
    }

    @Test
    @DisplayName("a Trip's UPI link pays its travels owner; without their UPI ID, the platform's")
    void upiLinkUsesBookingOwnersUpi() {
        TravelBookingRepository bookings = mock(TravelBookingRepository.class);
        OwnerRepository owners = mock(OwnerRepository.class);
        UUID trip = UUID.randomUUID();
        TravelBooking b = new TravelBooking();
        b.setId(trip);
        b.setOwnerId(ME);
        when(bookings.findById(trip)).thenReturn(Optional.of(b));
        Owner o = new Owner();
        o.setId(ME);
        o.setUpiId("ravitravels@okbank");
        o.setTravelsName("Ravi Travels");
        when(owners.findById(ME)).thenReturn(Optional.of(o));
        PaymentService service = payments(mock(PaymentRepository.class), bookings);
        ReflectionTestUtils.setField(service, "ownerRepository", owners);

        String link = ReflectionTestUtils.invokeMethod(service, "generateUpiDeepLink", new BigDecimal("500.00"), trip.toString());
        assertTrue(link.contains("pa=ravitravels%40okbank"), link);
        assertTrue(link.contains("pn=Ravi+Travels"), link);

        o.setUpiId(null);
        link = ReflectionTestUtils.invokeMethod(service, "generateUpiDeepLink", new BigDecimal("500.00"), trip.toString());
        assertTrue(link.contains("pa=platform%40bank"), link);
    }

    @Test
    @DisplayName("reviews are my Trips' only")
    void reviewsOnlyOwn() {
        ReviewRepository reviews = mock(ReviewRepository.class);
        Review mine = new Review();
        mine.setBookingId(UUID.randomUUID());
        ReflectionTestUtils.setField(mine, "ownerId", ME);
        Review theirs = new Review();
        theirs.setBookingId(UUID.randomUUID());
        ReflectionTestUtils.setField(theirs, "ownerId", OTHER);
        when(reviews.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(mine, theirs));
        ReviewService service = new ReviewService(reviews, mock(TravelBookingRepository.class), mock(DriverRepository.class));

        assertEquals(1, service.getReviewsFor(ME).size());
        assertEquals(0, service.getReviewsFor(null).size());
    }

    @Test
    @DisplayName("a package is the caller's own; anyone else's is not found")
    void packagesOwnedByCaller() {
        OwnerScope.bind(ME, false);
        TravelPackageRepository repo = mock(TravelPackageRepository.class);
        TravelPackage theirs = new TravelPackage();
        theirs.setId(3L);
        theirs.setOwnerId(OTHER);
        when(repo.findById(3L)).thenReturn(Optional.of(theirs));
        TravelPackageService service = new TravelPackageService(repo, mock(PackageBookingRepository.class),
                new com.fasterxml.jackson.databind.ObjectMapper());

        assertThrows(ResourceNotFoundException.class, () -> service.togglePackageStatus(3L));
        assertThrows(ResourceNotFoundException.class, () -> service.deletePackage(3L));
        verify(repo, never()).delete(any());
    }

    @Test
    @DisplayName("another owner's package booking cannot be confirmed, cancelled or completed")
    void otherOwnersPackageBooking() {
        OwnerScope.bind(ME, false);
        PackageBookingRepository repo = mock(PackageBookingRepository.class);
        PackageBooking b = new PackageBooking();
        UUID id = UUID.randomUUID();
        b.setId(id);
        ReflectionTestUtils.setField(b, "ownerId", OTHER);
        when(repo.findById(id)).thenReturn(Optional.of(b));
        PackageBookingService service = new PackageBookingService(repo, mock(TravelPackageRepository.class),
                mock(NotificationRepository.class));

        assertThrows(ResourceNotFoundException.class, () -> service.confirmBooking(id));
        assertThrows(ResourceNotFoundException.class, () -> service.cancelByOwner(id, "x"));
        assertThrows(ResourceNotFoundException.class, () -> service.completeBooking(id));
    }

    @Test
    @DisplayName("live tracking: an owner watches their own Trips; City rides only the platform owner")
    void guardAllowsOwnTripOnly() {
        TravelBookingRepository repo = mock(TravelBookingRepository.class);
        BookingAccessGuard guard = new BookingAccessGuard(repo);
        TripRateService rates = mock(TripRateService.class);
        when(rates.platformOwnerId()).thenReturn(1L);
        ReflectionTestUtils.setField(guard, "tripRates", rates);
        UUID trip = UUID.randomUUID();
        TravelBooking b = new TravelBooking();
        b.setId(trip);
        b.setOwnerId(ME);
        when(repo.findById(trip)).thenReturn(Optional.of(b));
        var owner = new UsernamePasswordAuthenticationToken("7", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        var other = new UsernamePasswordAuthenticationToken("8", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        var platform = new UsernamePasswordAuthenticationToken("1", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));

        assertTrue(guard.canAccess(owner, trip.toString()));
        assertFalse(guard.canAccess(other, trip.toString()));
        assertFalse(guard.canAccess(platform, trip.toString()), "the platform does not watch an owner's Trip");

        b.setOwnerId(null);
        assertTrue(guard.canAccess(platform, trip.toString()));
        assertFalse(guard.canAccess(owner, trip.toString()));
    }
}

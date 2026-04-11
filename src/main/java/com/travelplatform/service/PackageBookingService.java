package com.travelplatform.service;

import com.travelplatform.dto.PackageBookingRequest;
import com.travelplatform.dto.PackageBookingResponse;
import com.travelplatform.entity.Notification;
import com.travelplatform.entity.PackageBooking;
import com.travelplatform.entity.TravelPackage;
import com.travelplatform.repository.NotificationRepository;
import com.travelplatform.repository.PackageBookingRepository;
import com.travelplatform.repository.TravelPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PackageBookingService {

    private final PackageBookingRepository bookingRepository;
    private final TravelPackageRepository packageRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public PackageBookingResponse createBooking(UUID userId, PackageBookingRequest request) {
        TravelPackage pkg = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new RuntimeException("Package not found"));

        if (!pkg.getActive()) {
            throw new RuntimeException("This package is no longer available");
        }

        if (request.getNumberOfPersons() > pkg.getMaxGroupSize()) {
            throw new RuntimeException("Number of persons exceeds maximum group size of " + pkg.getMaxGroupSize());
        }

        PackageBooking booking = new PackageBooking();
        booking.setPackageId(pkg.getId());
        booking.setUserId(userId);
        booking.setUserName(request.getUserName());
        booking.setUserEmail(request.getUserEmail());
        booking.setUserPhone(request.getUserPhone());
        booking.setNumberOfPersons(request.getNumberOfPersons());
        booking.setTravelDate(request.getTravelDate());
        booking.setSpecialRequests(request.getSpecialRequests());
        booking.setTotalAmount(pkg.getPricePerPerson() * request.getNumberOfPersons());
        booking.setStatus(PackageBooking.BookingStatus.PENDING);

        // Snapshot package details
        booking.setPackageName(pkg.getName());
        booking.setPackageCategory(pkg.getCategory().name());
        booking.setDurationDays(pkg.getDurationDays());
        booking.setDurationNights(pkg.getDurationNights());
        booking.setPricePerPerson(pkg.getPricePerPerson());

        PackageBooking saved = bookingRepository.save(booking);

        // Notify owner
        try {
            Notification n = new Notification();
            n.setRecipientId("owner");
            n.setRecipientRole("ROLE_OWNER");
            n.setType("PACKAGE_BOOKING");
            n.setTitle("New Package Booking");
            n.setMessage(request.getUserName() + " booked \"" + pkg.getName() + "\" for " + request.getNumberOfPersons() + " persons");
            n.setBookingId(saved.getId().toString());
            n.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(n);
        } catch (Exception ignored) {}

        return toResponse(saved);
    }

    public List<PackageBookingResponse> getUserBookings(UUID userId) {
        return bookingRepository.findByUserIdOrderByBookingDateDesc(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<PackageBookingResponse> getAllBookings() {
        return bookingRepository.findAllByOrderByBookingDateDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<PackageBookingResponse> getBookingsByStatus(String status) {
        PackageBooking.BookingStatus bookingStatus = PackageBooking.BookingStatus.valueOf(status.toUpperCase());
        return bookingRepository.findByStatusOrderByBookingDateDesc(bookingStatus)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public PackageBookingResponse confirmBooking(UUID bookingId) {
        PackageBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        booking.setStatus(PackageBooking.BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());

        try {
            Notification n = new Notification();
            n.setRecipientId(booking.getUserId().toString());
            n.setRecipientRole("ROLE_USER");
            n.setType("PACKAGE_CONFIRMED");
            n.setTitle("Package Booking Confirmed");
            n.setMessage("Your booking for \"" + booking.getPackageName() + "\" has been confirmed!");
            n.setBookingId(booking.getId().toString());
            n.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(n);
        } catch (Exception ignored) {}

        return toResponse(bookingRepository.save(booking));
    }

    @Transactional
    public PackageBookingResponse cancelBooking(UUID bookingId, String reason) {
        PackageBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        booking.setStatus(PackageBooking.BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancellationReason(reason);
        return toResponse(bookingRepository.save(booking));
    }

    @Transactional
    public PackageBookingResponse completeBooking(UUID bookingId) {
        PackageBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        booking.setStatus(PackageBooking.BookingStatus.COMPLETED);
        return toResponse(bookingRepository.save(booking));
    }

    private PackageBookingResponse toResponse(PackageBooking b) {
        PackageBookingResponse res = new PackageBookingResponse();
        res.setId(b.getId());
        res.setPackageId(b.getPackageId());
        res.setPackageName(b.getPackageName());
        res.setPackageCategory(b.getPackageCategory());
        res.setDurationDays(b.getDurationDays());
        res.setDurationNights(b.getDurationNights());
        res.setPricePerPerson(b.getPricePerPerson());
        res.setUserId(b.getUserId());
        res.setUserName(b.getUserName());
        res.setUserEmail(b.getUserEmail());
        res.setUserPhone(b.getUserPhone());
        res.setNumberOfPersons(b.getNumberOfPersons());
        res.setTravelDate(b.getTravelDate());
        res.setSpecialRequests(b.getSpecialRequests());
        res.setTotalAmount(b.getTotalAmount());
        res.setStatus(b.getStatus().name());
        res.setBookingDate(b.getBookingDate());
        res.setConfirmedAt(b.getConfirmedAt());
        res.setCancelledAt(b.getCancelledAt());
        res.setCancellationReason(b.getCancellationReason());
        return res;
    }
}

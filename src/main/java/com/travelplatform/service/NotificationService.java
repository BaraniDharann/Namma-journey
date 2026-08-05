package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Notification;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.User;
import com.travelplatform.repository.NotificationRepository;
import com.travelplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final TelegramDispatchService telegramDispatchService;

    public void notifyBookingCreated(TravelBooking booking) {
        Notification notification = new Notification();
        notification.setRecipientId("owner");
        notification.setRecipientRole("ROLE_OWNER");
        notification.setType("BOOKING_CREATED");
        notification.setTitle("New Booking Received");
        notification.setMessage("New booking from " + booking.getUserName() + " — " +
                booking.getFromPlace() + " to " + booking.getToPlace() +
                " on " + booking.getFromDate() + ". Amount: ₹" + String.format("%.2f", booking.getTotalAmount()));
        notification.setBookingId(booking.getId().toString());
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
        log.info("Notification sent to owner for new booking: {}", booking.getId());
    }

    public void notifyDriverAssigned(TravelBooking booking, Driver driver) {
        Notification notification = new Notification();
        notification.setRecipientId(driver.getId().toString());
        notification.setRecipientRole("ROLE_DRIVER");
        notification.setType("DRIVER_ASSIGNED");
        notification.setTitle("New Trip Assigned");
        notification.setMessage("You have been assigned a new trip from " + booking.getFromPlace() +
                " to " + booking.getToPlace() + " on " + booking.getFromDate() +
                ". Passenger: " + booking.getUserName());
        notification.setBookingId(booking.getId().toString());
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);

        if (driver.getEmail() != null) {
            emailService.sendTripAssignedEmail(driver.getEmail(), driver.getName(), booking);
        }

        // The point of the whole Telegram channel: a driver at the wheel will not read an
        // email or open the web app, but their phone does alert for Telegram, and the card
        // carries an Accept button they can hit without navigating anywhere. Best-effort by
        // design - the assignment is already committed and the rows above remain the record
        // of it, so a Telegram outage must not fail the caller.
        try {
            telegramDispatchService.sendTripAssigned(booking, driver);
        } catch (Exception e) {
            log.warn("Telegram dispatch failed for driver {} on booking {}: {}",
                    driver.getId(), booking.getId(), e.toString());
        }

        log.info("Notification sent to driver {} for booking: {}", driver.getId(), booking.getId());
    }

    public void notifyTripAccepted(TravelBooking booking, Driver driver) {
        Notification notification = new Notification();
        notification.setRecipientId(booking.getUserId().toString());
        notification.setRecipientRole("ROLE_USER");
        notification.setType("TRIP_ACCEPTED");
        notification.setTitle("Driver Accepted Your Trip");
        notification.setMessage("Driver " + driver.getName() + " has accepted your trip from " +
                booking.getFromPlace() + " to " + booking.getToPlace() +
                ". Contact: " + driver.getMobile());
        notification.setBookingId(booking.getId().toString());
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);

        userRepository.findById(booking.getUserId()).ifPresent(user -> {
            emailService.sendTripAcceptedEmail(user.getEmail(), user.getName(), booking, driver);
        });
        log.info("Notification sent to user {} for trip accepted: {}", booking.getUserId(), booking.getId());
    }

    /**
     * @param replacement the driver the trip was automatically handed to, or null when no
     *                    available driver was left and the owner has to step in. The two cases
     *                    need materially different messages: one is progress, the other is a
     *                    task for the owner.
     */
    public void notifyTripRejected(TravelBooking booking, Driver driver, Driver replacement) {
        String route = booking.getFromPlace() + " to " + booking.getToPlace();

        // Notify user
        Notification userNotification = new Notification();
        userNotification.setRecipientId(booking.getUserId().toString());
        userNotification.setRecipientRole("ROLE_USER");
        userNotification.setType("TRIP_REJECTED");
        userNotification.setTitle(replacement != null ? "New Driver Assigned" : "Trip Reassignment in Progress");
        userNotification.setMessage(replacement != null
                ? "Driver " + driver.getName() + " was unable to take your trip from " + route +
                  ". Driver " + replacement.getName() + " has been assigned and will confirm shortly."
                : "Driver " + driver.getName() + " was unable to take your trip from " + route +
                  ". We are arranging another driver for you.");
        userNotification.setBookingId(booking.getId().toString());
        userNotification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(userNotification);

        userRepository.findById(booking.getUserId()).ifPresent(user -> {
            emailService.sendTripRejectedEmail(user.getEmail(), user.getName(), booking);
        });

        // Notify owner
        Notification ownerNotification = new Notification();
        ownerNotification.setRecipientId("owner");
        ownerNotification.setRecipientRole("ROLE_OWNER");
        ownerNotification.setType("TRIP_REJECTED");
        ownerNotification.setTitle(replacement != null ? "Trip Reassigned Automatically" : "Trip Needs a Driver");
        ownerNotification.setMessage(replacement != null
                ? "Driver " + driver.getName() + " rejected booking from " + route +
                  ". Automatically reassigned to " + replacement.getName() + "."
                : "Driver " + driver.getName() + " rejected booking from " + route +
                  ". No available driver remains — assign one manually.");
        ownerNotification.setBookingId(booking.getId().toString());
        ownerNotification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(ownerNotification);

        log.info("Trip rejected notifications sent for booking {} (reassigned to {})",
                booking.getId(), replacement != null ? replacement.getId() : "nobody");
    }

    /**
     * The driver has dropped the passenger off and the fare is now due.
     *
     * <p>Deliberately not worded as "completed": the booking is still STARTED at this point
     * and only reaches COMPLETED once the money is in (driver marks cash, or the owner
     * verifies the UPI transfer). Telling the passenger the trip is finished while their
     * payment screen still shows an outstanding amount is how support tickets are made.
     *
     * <p>Idempotent. The driver can tap End Trip more than once - an expired QR is
     * regenerated by the same call - and nobody should be notified twice for one trip.
     */
    public void notifyTripEnded(TravelBooking booking, Driver driver) {
        String bookingRef = booking.getId().toString();
        if (notificationRepository.existsByBookingIdAndType(bookingRef, "TRIP_ENDED")) {
            return;
        }

        String route = booking.getFromPlace() + " to " + booking.getToPlace();
        String amount = String.format("%.2f", booking.getTotalAmount());

        // Notify user
        Notification userNotification = new Notification();
        userNotification.setRecipientId(booking.getUserId().toString());
        userNotification.setRecipientRole("ROLE_USER");
        userNotification.setType("TRIP_ENDED");
        userNotification.setTitle("Trip Ended — Payment Due");
        userNotification.setMessage("Your trip from " + route + " has ended. Amount payable: ₹" +
                amount + ". Complete the payment to close the booking.");
        userNotification.setBookingId(bookingRef);
        userNotification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(userNotification);

        userRepository.findById(booking.getUserId()).ifPresent(user -> {
            emailService.sendTripEndedEmail(user.getEmail(), user.getName(), booking);
        });

        // Notify owner
        Notification ownerNotification = new Notification();
        ownerNotification.setRecipientId("owner");
        ownerNotification.setRecipientRole("ROLE_OWNER");
        ownerNotification.setType("TRIP_ENDED");
        ownerNotification.setTitle("Trip Ended — Awaiting Payment");
        ownerNotification.setMessage("Trip from " + route + " was ended by driver " + driver.getName() +
                ". ₹" + amount + " awaiting payment.");
        ownerNotification.setBookingId(bookingRef);
        ownerNotification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(ownerNotification);

        log.info("Trip ended notifications sent for booking: {}", booking.getId());
    }

    public void notifyPaymentReceived(TravelBooking booking) {
        Notification notification = new Notification();
        notification.setRecipientId("owner");
        notification.setRecipientRole("ROLE_OWNER");
        notification.setType("PAYMENT_RECEIVED");
        notification.setTitle("Payment Received");
        notification.setMessage("Payment of ₹" + String.format("%.2f", booking.getTotalAmount()) +
                " received for booking " + booking.getFromPlace() + " to " + booking.getToPlace() +
                " by " + booking.getUserName());
        notification.setBookingId(booking.getId().toString());
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);

        // Notify user
        Notification userNotification = new Notification();
        userNotification.setRecipientId(booking.getUserId().toString());
        userNotification.setRecipientRole("ROLE_USER");
        userNotification.setType("PAYMENT_VERIFIED");
        userNotification.setTitle("Payment Confirmed");
        userNotification.setMessage("Your payment of ₹" + String.format("%.2f", booking.getTotalAmount()) +
                " for the trip from " + booking.getFromPlace() + " to " + booking.getToPlace() +
                " has been confirmed.");
        userNotification.setBookingId(booking.getId().toString());
        userNotification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(userNotification);

        userRepository.findById(booking.getUserId()).ifPresent(user -> {
            emailService.sendPaymentConfirmationEmail(user.getEmail(), user.getName(), booking);
        });

        log.info("Payment notifications sent for booking: {}", booking.getId());
    }

    public List<Notification> getNotifications(String recipientId, String role) {
        return notificationRepository.findByRecipientIdAndRecipientRoleOrderByCreatedAtDesc(recipientId, role);
    }

    public long getUnreadCount(String recipientId, String role) {
        return notificationRepository.countByRecipientIdAndRecipientRoleAndReadFalse(recipientId, role);
    }

    @Transactional
    public void markAsRead(UUID notificationId, String requesterPrincipal) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            // Owner can mark any notification (including the shared "owner" bucket); other
            // roles can only mark notifications addressed to them. Without this check any
            // authenticated user could flip another user's notification to read.
            String recipient = notification.getRecipientId();
            boolean isOwnerBucket = "owner".equalsIgnoreCase(recipient);
            boolean isOwnNotification = recipient != null && recipient.equals(requesterPrincipal);
            if (!isOwnerBucket && !isOwnNotification) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Cannot mark another user's notification as read");
            }
            notification.setRead(true);
            notificationRepository.save(notification);
        });
    }

    @Transactional
    public void markAllAsRead(String recipientId, String role) {
        notificationRepository.markAllAsReadBulk(recipientId, role);
    }
}

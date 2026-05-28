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

    public void notifyTripRejected(TravelBooking booking, Driver driver) {
        // Notify user
        Notification userNotification = new Notification();
        userNotification.setRecipientId(booking.getUserId().toString());
        userNotification.setRecipientRole("ROLE_USER");
        userNotification.setType("TRIP_REJECTED");
        userNotification.setTitle("Trip Reassignment in Progress");
        userNotification.setMessage("Driver " + driver.getName() + " was unable to take your trip from " +
                booking.getFromPlace() + " to " + booking.getToPlace() +
                ". We are reassigning a new driver.");
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
        ownerNotification.setTitle("Driver Rejected Trip");
        ownerNotification.setMessage("Driver " + driver.getName() + " rejected booking from " +
                booking.getFromPlace() + " to " + booking.getToPlace() +
                ". Booking needs reassignment.");
        ownerNotification.setBookingId(booking.getId().toString());
        ownerNotification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(ownerNotification);

        log.info("Trip rejected notifications sent for booking: {}", booking.getId());
    }

    public void notifyTripEnded(TravelBooking booking, Driver driver) {
        // Notify user
        Notification userNotification = new Notification();
        userNotification.setRecipientId(booking.getUserId().toString());
        userNotification.setRecipientRole("ROLE_USER");
        userNotification.setType("TRIP_ENDED");
        userNotification.setTitle("Trip Completed");
        userNotification.setMessage("Your trip from " + booking.getFromPlace() + " to " +
                booking.getToPlace() + " has been completed. Thank you for traveling with Namma Journey!");
        userNotification.setBookingId(booking.getId().toString());
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
        ownerNotification.setTitle("Trip Completed");
        ownerNotification.setMessage("Trip from " + booking.getFromPlace() + " to " +
                booking.getToPlace() + " completed by driver " + driver.getName() +
                ". Amount: ₹" + String.format("%.2f", booking.getTotalAmount()));
        ownerNotification.setBookingId(booking.getId().toString());
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

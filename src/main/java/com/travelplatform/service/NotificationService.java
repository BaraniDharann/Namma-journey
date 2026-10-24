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
    private final AfterCommitDispatcher dispatcher;

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

    /** Tells the owner a driver applied (or re-uploaded after a rejection) and is waiting for review. */
    public void notifyDriverApplication(Driver d, boolean resubmitted) {
        Notification notification = new Notification();
        notification.setRecipientId("owner");
        notification.setRecipientRole("ROLE_OWNER");
        notification.setType("DRIVER_APPLICATION");
        notification.setTitle(resubmitted ? "Driver documents re-submitted" : "New driver application");
        notification.setMessage(d.getName() + " (" + d.getMobile() + ") "
                + (resubmitted ? "re-submitted documents" : "applied to drive")
                + " and is waiting for your review.");
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    public void notifyDriverApproved(Driver d) {
        notificationRepository.save(driverNotice(d, "DRIVER_APPROVED", "Account approved",
                "Your account has been approved. You can go online and take rides now."));
        if (d.getEmail() != null) {
            emailService.sendDriverApprovedEmail(d.getEmail(), d.getName());
        }
    }

    public void notifyDriverRejected(Driver d) {
        notificationRepository.save(driverNotice(d, "DRIVER_REJECTED", "Documents need attention",
                "Your application was not approved: " + d.getRejectionReason()
                        + " Please upload the corrected documents."));
        if (d.getEmail() != null) {
            emailService.sendDriverRejectedEmail(d.getEmail(), d.getName(), d.getRejectionReason());
        }
    }

    private Notification driverNotice(Driver d, String type, String title, String message) {
        Notification n = new Notification();
        n.setRecipientId(d.getId().toString());
        n.setRecipientRole("ROLE_DRIVER");
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setCreatedAt(LocalDateTime.now());
        return n;
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
        // carries an Accept button they can hit without navigating anywhere. Sent only after
        // commit - a card for a booking that then rolled back would have an Accept button for
        // a trip that does not exist - and best-effort: the row above stays the record of it,
        // so a Telegram outage never fails the caller (AfterCommitDispatcher logs it).
        dispatcher.dispatch("telegram", () -> telegramDispatchService.sendTripAssigned(booking, driver));

        log.info("Notification sent to driver {} for booking: {}", driver.getId(), booking.getId());
    }

    /**
     * A Ride Now offer: this driver is the nearest, and has seconds to take it. No email - it
     * would arrive after the offer is gone. The Telegram card carries the same Accept button as
     * an assignment; accepting after the deadline is refused there too.
     */
    public void notifyRideOffered(TravelBooking booking, Driver driver) {
        Notification notification = new Notification();
        notification.setRecipientId(driver.getId().toString());
        notification.setRecipientRole("ROLE_DRIVER");
        notification.setType("RIDE_OFFERED");
        notification.setTitle("New ride nearby");
        notification.setMessage("Pickup " + (booking.getOfferDistanceKm() != null
                ? String.format(java.util.Locale.ROOT, "%.1f km away", booking.getOfferDistanceKm()) : "near you")
                + " at " + booking.getFromPlace() + ", going to " + booking.getToPlace()
                + ". Accept within " + secondsLeft(booking) + " seconds.");
        notification.setBookingId(booking.getId().toString());
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);

        dispatcher.dispatch("telegram", () -> telegramDispatchService.sendTripAssigned(booking, driver));
    }

    /** Nobody nearby took a Ride Now booking in time: the owner places it, the passenger is told. */
    public void notifyNoDriverNearby(TravelBooking booking) {
        String route = booking.getFromPlace() + " to " + booking.getToPlace();

        Notification owner = new Notification();
        owner.setRecipientId("owner");
        owner.setRecipientRole("ROLE_OWNER");
        owner.setType("RIDE_NO_DRIVER");
        owner.setTitle("Ride Now needs a driver");
        owner.setMessage("No driver near the pickup took the ride from " + route
                + " for " + booking.getUserName() + ". Assign one manually.");
        owner.setBookingId(booking.getId().toString());
        owner.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(owner);

        Notification user = new Notification();
        user.setRecipientId(booking.getUserId().toString());
        user.setRecipientRole("ROLE_USER");
        user.setType("RIDE_NO_DRIVER");
        user.setTitle("Still finding your driver");
        user.setMessage("No driver near " + booking.getFromPlace()
                + " is free right now. Our team is assigning one to you by hand.");
        user.setBookingId(booking.getId().toString());
        user.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(user);
    }

    private static long secondsLeft(TravelBooking booking) {
        if (booking.getOfferExpiresAt() == null) {
            return 0;
        }
        return Math.max(0, java.time.Duration.between(
                LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")), booking.getOfferExpiresAt()).getSeconds());
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

    /** The passenger cancelled: the owner and the assigned driver (if any) need to know. */
    public void notifyBookingCancelled(TravelBooking booking) {
        String route = booking.getFromPlace() + " to " + booking.getToPlace();
        Notification owner = new Notification();
        owner.setRecipientId("owner");
        owner.setRecipientRole("ROLE_OWNER");
        owner.setType("BOOKING_CANCELLED");
        owner.setTitle("Booking Cancelled");
        owner.setMessage(booking.getUserName() + " cancelled the trip from " + route + " on " + booking.getFromDate() + ".");
        owner.setBookingId(booking.getId().toString());
        owner.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(owner);

        if (booking.getDriverId() != null) {
            Notification driver = new Notification();
            driver.setRecipientId(booking.getDriverId().toString());
            driver.setRecipientRole("ROLE_DRIVER");
            driver.setType("BOOKING_CANCELLED");
            driver.setTitle("Trip Cancelled");
            driver.setMessage("The passenger cancelled the trip from " + route + " on " + booking.getFromDate() + ".");
            driver.setBookingId(booking.getId().toString());
            driver.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(driver);
        }
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

    /** The Trip's advance is in, so the hold is now a real booking. */
    public void notifyAdvanceReceived(TravelBooking booking, java.math.BigDecimal advance) {
        String amount = String.format(java.util.Locale.ROOT, "%.2f", advance);
        Notification owner = new Notification();
        owner.setRecipientId("owner");
        owner.setRecipientRole("ROLE_OWNER");
        owner.setType("ADVANCE_RECEIVED");
        owner.setTitle("Advance received");
        owner.setMessage("Advance of ₹" + amount + " received for " + booking.getFromPlace() + " to "
                + booking.getToPlace() + " by " + booking.getUserName());
        owner.setBookingId(booking.getId().toString());
        owner.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(owner);

        Notification user = new Notification();
        user.setRecipientId(booking.getUserId().toString());
        user.setRecipientRole("ROLE_USER");
        user.setType("ADVANCE_RECEIVED");
        user.setTitle("Trip booked");
        user.setMessage("We received your advance of ₹" + amount + ". Your trip from " + booking.getFromPlace()
                + " to " + booking.getToPlace() + " is booked; the balance is paid at the end of the trip.");
        user.setBookingId(booking.getId().toString());
        user.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(user);

        userRepository.findById(booking.getUserId()).ifPresent(u ->
                emailService.sendAdvanceReceivedEmail(u.getEmail(), u.getName(), booking, advance));
    }

    /** An unpaid hold ran out and the car was released. In-app only: no email for a booking never paid. */
    public void notifyHoldReleased(TravelBooking booking) {
        Notification user = new Notification();
        user.setRecipientId(booking.getUserId().toString());
        user.setRecipientRole("ROLE_USER");
        user.setType("HOLD_RELEASED");
        user.setTitle("Booking released");
        user.setMessage("Your trip from " + booking.getFromPlace() + " to " + booking.getToPlace()
                + " was released because the advance was not paid in time. You can book it again.");
        user.setBookingId(booking.getId().toString());
        user.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(user);
    }

    public List<Notification> getNotifications(String recipientId, String role) {
        return notificationRepository.findByRecipientIdAndRecipientRoleOrderByCreatedAtDesc(recipientId, role);
    }

    public long getUnreadCount(String recipientId, String role) {
        return notificationRepository.countByRecipientIdAndRecipientRoleAndReadFalse(recipientId, role);
    }

    @Transactional
    public void markAsRead(UUID notificationId, String requesterPrincipal, boolean requesterIsOwner) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            // The shared "owner" bucket belongs to the owner alone - any signed-in passenger or
            // driver used to be able to clear the owner's alerts. Everyone else may only mark
            // notifications addressed to them.
            String recipient = notification.getRecipientId();
            boolean isOwnerBucket = "owner".equalsIgnoreCase(recipient);
            boolean allowed = isOwnerBucket ? requesterIsOwner
                    : recipient != null && recipient.equals(requesterPrincipal);
            if (!allowed) {
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

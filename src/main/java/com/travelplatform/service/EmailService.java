package com.travelplatform.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.web.util.HtmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${mail.from.name:Namma Journey}")
    private String fromName;

    /**
     * Master switch for outbound delivery. Set false for test and CI runs.
     *
     * <p>{@code otp.test.mode} only stops OTP mail — every other notification (driver
     * credentials, trip assigned/accepted/ended, payment confirmations) still went out over
     * real SMTP. An end-to-end run therefore mailed real people: each pass delivered "Trip
     * assigned" notices to live addresses held by fixture bookings. Suppressing here rather
     * than at each call site means no future notification can be added and silently escape.
     */
    @Value("${mail.delivery.enabled:true}")
    private boolean deliveryEnabled;

    /** Outbox job type for queued notification emails; handled by EmailJobHandler. */
    public static final String EMAIL_JOB = "email";

    @Autowired
    private com.travelplatform.outbox.Outbox outbox;

    /**
     * Hand a prepared message to the mail server, unless delivery is switched off. Callers use
     * this instead of {@code mailSender.send} directly.
     */
    private boolean deliver(MimeMessage message) throws Exception {
        if (!deliveryEnabled) {
            log.info("mail.delivery.enabled=false — suppressed outbound email to {}",
                    java.util.Arrays.toString(message.getAllRecipients()));
            return false;
        }
        mailSender.send(message);
        return true;
    }

    private static final String HTML_HEADER =
            "<html><body style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>" +
            "<div style='background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);padding:30px;text-align:center;border-radius:10px 10px 0 0;'>" +
            "<h1 style='color:white;margin:0;'>Namma Journey</h1></div>" +
            "<div style='padding:30px;background:#ffffff;border:1px solid #e0e0e0;'>";
    private static final String HTML_FOOTER =
            "<br><p style='color:#888;font-size:12px;'>This is an automated message from Namma Journey. Please do not reply.</p>" +
            "</div><div style='background:#f5f5f5;padding:15px;text-align:center;border-radius:0 0 10px 10px;border:1px solid #e0e0e0;border-top:none;'>" +
            "<p style='color:#888;margin:0;font-size:12px;'>© Namma Journey. All rights reserved.</p></div></body></html>";

    @Async("emailExecutor")
    public void sendDriverCredentials(String toEmail, String driverName, String username, String password) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Your Driver Account Credentials - Namma Journey");
            helper.setText(String.format(
                "<html><body>" +
                "<h2>Welcome to Namma Journey!</h2>" +
                "<p>Dear %s,</p>" +
                "<p>Your driver account has been created successfully. Here are your login credentials:</p>" +
                "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                "<p><strong>Username (Mobile):</strong> %s</p>" +
                "<p><strong>Password:</strong> %s</p>" +
                "</div>" +
                "<p>Please keep these credentials secure and change your password after first login.</p>" +
                "<br><p>Best regards,<br>Namma Journey Team</p>" +
                "</body></html>",
                driverName, username, password
            ), true);
            if (deliver(message)) {
                log.info("Driver credentials email sent successfully to: {}", toEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send driver credentials email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async("emailExecutor")
    public void sendOtpEmail(String toEmail, String otp, int expiryMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Your OTP for Namma Journey");
            helper.setText(String.format(
                "<html><body>" +
                "<h2>Your OTP Code</h2>" +
                "<p>Your OTP for Namma Journey is: <strong style='font-size:24px;color:#007bff;'>%s</strong></p>" +
                "<p>This OTP is valid for %d minutes.</p>" +
                "<p>If you didn't request this OTP, please ignore this email.</p>" +
                "<br><p>Best regards,<br>Namma Journey Team</p>" +
                "</body></html>",
                otp, expiryMinutes
            ), true);
            if (deliver(message)) {
                log.info("OTP email sent successfully to: {}", toEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
        }
    }


    // ---- Notifications that must arrive: queued through the outbox --------------------------
    //
    // These used to be @Async sends that swallowed every error, run after commit from an
    // in-memory pool: a restart or an SMTP hiccup lost them silently. Now each composes its
    // message and writes it to the outbox in the caller's transaction; EmailJobHandler sends it
    // and a failure is retried with backoff. OTP and credential mails above stay direct - the
    // person is waiting on the screen for them, and a late OTP is worse than a failed one.

    /** A composed email, as stored in the outbox. */
    public record EmailMessage(String to, String subject, String html) {
    }

    public void sendDriverApprovedEmail(String toEmail, String name) {
        queue(toEmail, "Your Namma Journey driver account is approved",
                "<html><body><h2>You are approved</h2><p>Hi " + HtmlUtils.htmlEscape(name == null ? "" : name)
                + ", the owner has approved your documents. You can go online and take rides now.</p>"
                + "<br><p>Best regards,<br>Namma Journey Team</p></body></html>");
    }

    public void sendDriverRejectedEmail(String toEmail, String name, String reason) {
        queue(toEmail, "Your Namma Journey application needs changes",
                "<html><body><h2>Application not approved</h2><p>Hi " + HtmlUtils.htmlEscape(name == null ? "" : name)
                + ", the owner could not approve your documents.</p><p><strong>Reason:</strong> "
                + HtmlUtils.htmlEscape(reason == null ? "" : reason)
                + "</p><p>Sign in and upload the corrected documents to apply again.</p>"
                + "<br><p>Best regards,<br>Namma Journey Team</p></body></html>");
    }

    public void sendTripAssignedEmail(String driverEmail, String driverName, com.travelplatform.entity.TravelBooking booking) {
        queue(driverEmail, "New Trip Assigned - Namma Journey", HTML_HEADER +
                "<h2 style='color:#667eea;'>New Trip Assigned!</h2>" +
                "<p>Dear " + esc(driverName) + ",</p>" +
                "<p>You have been assigned a new trip. Here are the details:</p>" +
                "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                "<p><strong>From:</strong> " + esc(booking.getFromPlace()) + "</p>" +
                "<p><strong>To:</strong> " + esc(booking.getToPlace()) + "</p>" +
                "<p><strong>Date:</strong> " + booking.getFromDate() + " to " + booking.getToDate() + "</p>" +
                "<p><strong>Passenger:</strong> " + esc(booking.getUserName()) + "</p>" +
                "<p><strong>Contact:</strong> " + esc(booking.getUserPhone()) + "</p>" +
                "<p><strong>Distance:</strong> " + String.format(java.util.Locale.ROOT, "%.1f", booking.getDistanceKm()) + " km</p>" +
                "</div>" +
                "<p>Please log in to your account to accept or reject this trip.</p>" +
                HTML_FOOTER);
    }

    public void sendTripAcceptedEmail(String userEmail, String userName, com.travelplatform.entity.TravelBooking booking, com.travelplatform.entity.Driver driver) {
        queue(userEmail, "Driver Confirmed Your Trip - Namma Journey", HTML_HEADER +
                "<h2 style='color:#667eea;'>Your Trip is Confirmed!</h2>" +
                "<p>Dear " + esc(userName) + ",</p>" +
                "<p>Great news! A driver has accepted your trip.</p>" +
                "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                "<p><strong>Driver:</strong> " + esc(driver.getName()) + "</p>" +
                "<p><strong>Contact:</strong> " + esc(driver.getMobile()) + "</p>" +
                "<p><strong>From:</strong> " + esc(booking.getFromPlace()) + "</p>" +
                "<p><strong>To:</strong> " + esc(booking.getToPlace()) + "</p>" +
                "<p><strong>Date:</strong> " + booking.getFromDate() + " to " + booking.getToDate() + "</p>" +
                "</div>" +
                "<p>Have a safe and pleasant journey!</p>" +
                HTML_FOOTER);
    }

    public void sendTripRejectedEmail(String userEmail, String userName, com.travelplatform.entity.TravelBooking booking) {
        queue(userEmail, "Trip Update - Driver Reassignment - Namma Journey", HTML_HEADER +
                "<h2 style='color:#667eea;'>Trip Update</h2>" +
                "<p>Dear " + esc(userName) + ",</p>" +
                "<p>The previously assigned driver was unable to take your trip. We are working on assigning a new driver.</p>" +
                "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                "<p><strong>From:</strong> " + esc(booking.getFromPlace()) + "</p>" +
                "<p><strong>To:</strong> " + esc(booking.getToPlace()) + "</p>" +
                "<p><strong>Date:</strong> " + booking.getFromDate() + " to " + booking.getToDate() + "</p>" +
                "</div>" +
                "<p>We apologize for the inconvenience and will update you shortly.</p>" +
                HTML_FOOTER);
    }

    public void sendTripEndedEmail(String userEmail, String userName, com.travelplatform.entity.TravelBooking booking) {
        // Sent when the driver ends the trip, which is before the fare is settled - so this asks
        // for payment rather than declaring the booking finished. The "all done" message is
        // sendPaymentConfirmationEmail, which goes out once the money is in.
        queue(userEmail, "Trip Ended - Payment Due - Namma Journey", HTML_HEADER +
                "<h2 style='color:#667eea;'>Your trip has ended</h2>" +
                "<p>Dear " + esc(userName) + ",</p>" +
                "<p>Your driver has ended the trip. Please complete the payment to close this booking.</p>" +
                "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                "<p><strong>From:</strong> " + esc(booking.getFromPlace()) + "</p>" +
                "<p><strong>To:</strong> " + esc(booking.getToPlace()) + "</p>" +
                "<p><strong>Distance:</strong> " + String.format(java.util.Locale.ROOT, "%.1f", booking.getDistanceKm()) + " km</p>" +
                "<p><strong>Amount payable:</strong> ₹" + String.format(java.util.Locale.ROOT, "%.2f", booking.getTotalAmount()) + "</p>" +
                "</div>" +
                "<p>Pay online, scan the driver's QR code, or hand the fare over in cash. " +
                "You will get a confirmation email once the payment is recorded.</p>" +
                "<p>Thank you for choosing Namma Journey. We hope you had a great experience!</p>" +
                HTML_FOOTER);
    }

    public void sendPaymentConfirmationEmail(String email, String name, com.travelplatform.entity.TravelBooking booking) {
        queue(email, "Payment Confirmed - Namma Journey", HTML_HEADER +
                "<h2 style='color:#667eea;'>Payment Confirmed!</h2>" +
                "<p>Dear " + esc(name) + ",</p>" +
                "<p>Your payment has been confirmed successfully.</p>" +
                "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                "<p><strong>Trip:</strong> " + esc(booking.getFromPlace()) + " to " + esc(booking.getToPlace()) + "</p>" +
                "<p><strong>Amount:</strong> ₹" + String.format(java.util.Locale.ROOT, "%.2f", booking.getTotalAmount()) + "</p>" +
                "<p><strong>Status:</strong> Verified</p>" +
                "</div>" +
                "<p>Thank you for your payment!</p>" +
                HTML_FOOTER);
    }

    public void sendAdvanceReceivedEmail(String email, String name, com.travelplatform.entity.TravelBooking booking, java.math.BigDecimal advance) {
        queue(email, "Trip Booked - Advance Received - Namma Journey", HTML_HEADER +
                "<h2 style='color:#667eea;'>Your trip is booked</h2>" +
                "<p>Dear " + esc(name) + ",</p>" +
                "<p>We received your advance, so your car is reserved.</p>" +
                "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                "<p><strong>Trip:</strong> " + esc(booking.getFromPlace()) + " to " + esc(booking.getToPlace()) + "</p>" +
                "<p><strong>Date:</strong> " + booking.getFromDate() + " to " + booking.getToDate() + "</p>" +
                "<p><strong>Advance paid:</strong> ₹" + String.format(java.util.Locale.ROOT, "%.2f", advance) + "</p>" +
                "<p><strong>Balance at trip end:</strong> ₹" + String.format(java.util.Locale.ROOT, "%.2f",
                        booking.getTotalAmount().subtract(advance)) + "</p>" +
                "</div>" +
                "<p>Cancel more than 24 hours before the trip day and the advance is refunded in full.</p>" +
                HTML_FOOTER);
    }

    public void sendRefundStartedEmail(String email, String name, com.travelplatform.entity.TravelBooking booking, java.math.BigDecimal amount) {
        queue(email, "Refund Started - Namma Journey", HTML_HEADER +
                "<h2 style='color:#667eea;'>Your refund is on its way</h2>" +
                "<p>Dear " + esc(name) + ",</p>" +
                "<p>Your trip was cancelled before it started, so we are refunding what you paid online.</p>" +
                "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                "<p><strong>Trip:</strong> " + esc(booking.getFromPlace()) + " to " + esc(booking.getToPlace()) + "</p>" +
                "<p><strong>Refund:</strong> ₹" + String.format(java.util.Locale.ROOT, "%.2f", amount) + "</p>" +
                "</div>" +
                "<p>It goes back to the card, UPI account or bank you paid with, usually within 5-7 working days.</p>" +
                HTML_FOOTER);
    }

    private void queue(String to, String subject, String html) {
        if (to == null || to.isBlank()) {
            return;
        }
        outbox.enqueue(EMAIL_JOB, new EmailMessage(to, subject, html));
    }

    /** Sends a queued message now. Throws so the outbox retries a failed send. */
    public void sendNow(EmailMessage email) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(fromEmail, fromName);
        helper.setTo(email.to());
        helper.setSubject(email.subject());
        helper.setText(email.html(), true);
        if (deliver(message)) {
            log.info("Email '{}' sent to: {}", email.subject(), email.to());
        }
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}

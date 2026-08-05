package com.travelplatform.service;

import jakarta.mail.internet.MimeMessage;
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

    @Async("emailExecutor")
    public void sendTripAssignedEmail(String driverEmail, String driverName, com.travelplatform.entity.TravelBooking booking) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail, fromName);
            helper.setTo(driverEmail);
            helper.setSubject("New Trip Assigned - Namma Journey");
            helper.setText(HTML_HEADER +
                    "<h2 style='color:#667eea;'>New Trip Assigned!</h2>" +
                    "<p>Dear " + driverName + ",</p>" +
                    "<p>You have been assigned a new trip. Here are the details:</p>" +
                    "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                    "<p><strong>From:</strong> " + booking.getFromPlace() + "</p>" +
                    "<p><strong>To:</strong> " + booking.getToPlace() + "</p>" +
                    "<p><strong>Date:</strong> " + booking.getFromDate() + " to " + booking.getToDate() + "</p>" +
                    "<p><strong>Passenger:</strong> " + booking.getUserName() + "</p>" +
                    "<p><strong>Contact:</strong> " + booking.getUserPhone() + "</p>" +
                    "<p><strong>Distance:</strong> " + String.format("%.1f", booking.getDistanceKm()) + " km</p>" +
                    "</div>" +
                    "<p>Please log in to your account to accept or reject this trip.</p>" +
                    HTML_FOOTER, true);
            if (deliver(message)) {
                log.info("Trip assigned email sent to driver: {}", driverEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send trip assigned email: {}", e.getMessage());
        }
    }

    @Async("emailExecutor")
    public void sendTripAcceptedEmail(String userEmail, String userName, com.travelplatform.entity.TravelBooking booking, com.travelplatform.entity.Driver driver) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail, fromName);
            helper.setTo(userEmail);
            helper.setSubject("Driver Confirmed Your Trip - Namma Journey");
            helper.setText(HTML_HEADER +
                    "<h2 style='color:#667eea;'>Your Trip is Confirmed!</h2>" +
                    "<p>Dear " + userName + ",</p>" +
                    "<p>Great news! A driver has accepted your trip.</p>" +
                    "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                    "<p><strong>Driver:</strong> " + driver.getName() + "</p>" +
                    "<p><strong>Contact:</strong> " + driver.getMobile() + "</p>" +
                    "<p><strong>From:</strong> " + booking.getFromPlace() + "</p>" +
                    "<p><strong>To:</strong> " + booking.getToPlace() + "</p>" +
                    "<p><strong>Date:</strong> " + booking.getFromDate() + " to " + booking.getToDate() + "</p>" +
                    "</div>" +
                    "<p>Have a safe and pleasant journey!</p>" +
                    HTML_FOOTER, true);
            if (deliver(message)) {
                log.info("Trip accepted email sent to user: {}", userEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send trip accepted email: {}", e.getMessage());
        }
    }

    @Async("emailExecutor")
    public void sendTripRejectedEmail(String userEmail, String userName, com.travelplatform.entity.TravelBooking booking) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail, fromName);
            helper.setTo(userEmail);
            helper.setSubject("Trip Update - Driver Reassignment - Namma Journey");
            helper.setText(HTML_HEADER +
                    "<h2 style='color:#667eea;'>Trip Update</h2>" +
                    "<p>Dear " + userName + ",</p>" +
                    "<p>The previously assigned driver was unable to take your trip. We are working on assigning a new driver.</p>" +
                    "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                    "<p><strong>From:</strong> " + booking.getFromPlace() + "</p>" +
                    "<p><strong>To:</strong> " + booking.getToPlace() + "</p>" +
                    "<p><strong>Date:</strong> " + booking.getFromDate() + " to " + booking.getToDate() + "</p>" +
                    "</div>" +
                    "<p>We apologize for the inconvenience and will update you shortly.</p>" +
                    HTML_FOOTER, true);
            if (deliver(message)) {
                log.info("Trip rejected email sent to user: {}", userEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send trip rejected email: {}", e.getMessage());
        }
    }

    @Async("emailExecutor")
    public void sendTripEndedEmail(String userEmail, String userName, com.travelplatform.entity.TravelBooking booking) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail, fromName);
            helper.setTo(userEmail);
            // Sent when the driver ends the trip, which is before the fare is settled — so this
            // asks for payment rather than declaring the booking finished. The "all done"
            // message is sendPaymentConfirmationEmail, which goes out once the money is in.
            helper.setSubject("Trip Ended - Payment Due - Namma Journey");
            helper.setText(HTML_HEADER +
                    "<h2 style='color:#667eea;'>Your trip has ended</h2>" +
                    "<p>Dear " + userName + ",</p>" +
                    "<p>Your driver has ended the trip. Please complete the payment to close this booking.</p>" +
                    "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                    "<p><strong>From:</strong> " + booking.getFromPlace() + "</p>" +
                    "<p><strong>To:</strong> " + booking.getToPlace() + "</p>" +
                    "<p><strong>Distance:</strong> " + String.format("%.1f", booking.getDistanceKm()) + " km</p>" +
                    "<p><strong>Amount payable:</strong> ₹" + String.format("%.2f", booking.getTotalAmount()) + "</p>" +
                    "</div>" +
                    "<p>Pay by scanning the driver's QR code, or hand the fare over in cash. " +
                    "You will get a confirmation email once the payment is recorded.</p>" +
                    "<p>Thank you for choosing Namma Journey. We hope you had a great experience!</p>" +
                    HTML_FOOTER, true);
            if (deliver(message)) {
                log.info("Trip ended email sent to user: {}", userEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send trip ended email: {}", e.getMessage());
        }
    }

    @Async("emailExecutor")
    public void sendPaymentConfirmationEmail(String email, String name, com.travelplatform.entity.TravelBooking booking) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail, fromName);
            helper.setTo(email);
            helper.setSubject("Payment Confirmed - Namma Journey");
            helper.setText(HTML_HEADER +
                    "<h2 style='color:#667eea;'>Payment Confirmed!</h2>" +
                    "<p>Dear " + name + ",</p>" +
                    "<p>Your payment has been confirmed successfully.</p>" +
                    "<div style='background-color:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>" +
                    "<p><strong>Trip:</strong> " + booking.getFromPlace() + " to " + booking.getToPlace() + "</p>" +
                    "<p><strong>Amount:</strong> ₹" + String.format("%.2f", booking.getTotalAmount()) + "</p>" +
                    "<p><strong>Status:</strong> Verified</p>" +
                    "</div>" +
                    "<p>Thank you for your payment!</p>" +
                    HTML_FOOTER, true);
            if (deliver(message)) {
                log.info("Payment confirmation email sent to: {}", email);
            }
        } catch (Exception e) {
            log.error("Failed to send payment confirmation email: {}", e.getMessage());
        }
    }
}

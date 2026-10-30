package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Notification;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static com.travelplatform.service.TelegramClient.escapeHtml;

/**
 * Approves self-signup (INDEPENDENT) drivers on their own once their deadline passes (V24), and
 * carries out the owner's Approve / Reject / Hold presses from the Telegram group.
 *
 * <p>Owner-fleet drivers never come through here: the owner creates them already approved.
 *
 * <p>Automatic approval is only as good as {@link DriverApplicationChecks}: it catches typos and
 * missing uploads, not forged documents. An application that fails a check is held for the
 * owner rather than approved, and the owner is told why.
 */
@Service
public class DriverAutoApprovalService {

    private static final Logger log = LoggerFactory.getLogger(DriverAutoApprovalService.class);

    static final String TELEGRAM_REJECT_REASON =
            "Your documents could not be verified. Please upload clear photos of your licence, Aadhaar and vehicle RC.";

    private final DriverRepository driverRepository;
    private final DriverVerificationService verificationService;
    private final OwnerTelegramAlerts alerts;
    private final NotificationRepository notifications;
    private final TransactionTemplate tx;

    @Value("${app.drivers.auto-approve:true}")
    private boolean enabled = true;

    public DriverAutoApprovalService(DriverRepository driverRepository, DriverVerificationService verificationService,
                                     OwnerTelegramAlerts alerts, NotificationRepository notifications,
                                     TransactionTemplate tx) {
        this.driverRepository = driverRepository;
        this.verificationService = verificationService;
        this.alerts = alerts;
        this.notifications = notifications;
        this.tx = tx;
    }

    /** Every minute: approve (or hold, if a check fails) each application whose time has come. */
    @Scheduled(fixedDelayString = "${app.drivers.auto-approve-interval-ms:60000}", initialDelayString = "45000")
    public void sweep() {
        if (!enabled) {
            return;
        }
        List<Driver> due = driverRepository.findByFleetAndVerificationStatusAndEmailVerifiedTrueAndAutoApproveAtLessThanEqual(
                Driver.Fleet.INDEPENDENT, Driver.Verification.UNDER_REVIEW, LocalDateTime.now());
        for (Driver d : due) {
            try {
                tx.executeWithoutResult(s -> autoApproveOne(d.getId()));
            } catch (OptimisticLockingFailureException raced) {
                log.info("Driver {} was decided by the owner while auto-approving; leaving it", d.getId());
            } catch (RuntimeException e) {
                log.warn("Auto-approval of driver {} failed: {}", d.getId(), e.toString());
            }
        }
    }

    void autoApproveOne(Long driverId) {
        Driver d = driverRepository.findById(driverId).orElse(null);
        if (d == null || d.getFleet() != Driver.Fleet.INDEPENDENT
                || d.getVerificationStatus() != Driver.Verification.UNDER_REVIEW
                || d.getAutoApproveAt() == null || d.getAutoApproveAt().isAfter(LocalDateTime.now())) {
            return;
        }

        List<String> problems = DriverApplicationChecks.problems(d);
        if (!problems.isEmpty()) {
            driverRepository.holdAutoApproval(driverId, Driver.Verification.UNDER_REVIEW);
            String why = String.join("; ", problems);
            ownerNotice("DRIVER_NEEDS_REVIEW", "Driver needs your review",
                    d.getName() + " (" + d.getMobile() + ") was not approved automatically: " + why + ".");
            alerts.send("⚠️ <b>Not approved automatically</b>\n\n" + OwnerTelegramAlerts.driverLines(d)
                            + "\n<b>Why:</b> " + escapeHtml(why) + "\n\nCheck the documents and decide.",
                    OwnerTelegramAlerts.decisionButtons(driverId), null);
            log.info("Driver {} held for the owner: {}", driverId, why);
            return;
        }

        verificationService.approve(driverId, null);
        ownerNotice("DRIVER_AUTO_APPROVED", "Driver approved automatically",
                d.getName() + " (" + d.getMobile() + ") was approved automatically. "
                        + "You can still block them from the Drivers page.");
        alerts.send("✅ <b>Driver approved automatically</b>\n\n" + OwnerTelegramAlerts.driverLines(d), List.of(), null);
        log.info("Driver {} approved automatically", driverId);
    }

    /**
     * An owner button pressed in the Telegram group. The caller has already checked the press
     * came from that group.
     *
     * @param actor who pressed, for the message and the log ("Priya")
     * @return the toast and the line appended to the message
     */
    public OwnerTelegramAlerts.Outcome handleTelegram(String data, String actor) {
        String action;
        long driverId;
        try {
            if (data.startsWith(OwnerTelegramAlerts.APPROVE)) {
                action = "approve";
                driverId = Long.parseLong(data.substring(OwnerTelegramAlerts.APPROVE.length()));
            } else if (data.startsWith(OwnerTelegramAlerts.REJECT)) {
                action = "reject";
                driverId = Long.parseLong(data.substring(OwnerTelegramAlerts.REJECT.length()));
            } else {
                action = "hold";
                driverId = Long.parseLong(data.substring(OwnerTelegramAlerts.HOLD.length()));
            }
        } catch (NumberFormatException e) {
            return new OwnerTelegramAlerts.Outcome("Unknown action", null, List.of());
        }

        String who = escapeHtml(actor);
        try {
            switch (action) {
                case "approve" -> {
                    verificationService.approve(driverId, null);
                    log.info("Driver {} approved from Telegram by {}", driverId, actor);
                    return new OwnerTelegramAlerts.Outcome("Approved", "✅ <b>Approved</b> by " + who, List.of());
                }
                case "reject" -> {
                    verificationService.reject(driverId, TELEGRAM_REJECT_REASON, null);
                    log.info("Driver {} rejected from Telegram by {}", driverId, actor);
                    return new OwnerTelegramAlerts.Outcome("Rejected", "❌ <b>Rejected</b> by " + who
                            + ". The driver was asked to re-upload clear documents.", List.of());
                }
                default -> {
                    Integer rows = tx.execute(s -> driverRepository.holdAutoApproval(driverId, Driver.Verification.UNDER_REVIEW));
                    if (rows == null || rows == 0) {
                        return new OwnerTelegramAlerts.Outcome("Already decided", "ℹ️ Already decided - see the Applications page.", List.of());
                    }
                    log.info("Driver {} held from Telegram by {}", driverId, actor);
                    return new OwnerTelegramAlerts.Outcome("Held", "⏸ <b>Held</b> by " + who
                            + " - not approved automatically. Decide on the Applications page, or with the buttons below.",
                            OwnerTelegramAlerts.decisionButtons(driverId));
                }
            }
        } catch (OptimisticLockingFailureException | IllegalArgumentException
                 | com.travelplatform.exception.ResourceNotFoundException e) {
            return new OwnerTelegramAlerts.Outcome("Already decided", "ℹ️ Already decided - see the Applications page.", List.of());
        }
    }

    private void ownerNotice(String type, String title, String message) {
        Notification n = new Notification();
        n.setRecipientId("owner");
        n.setRecipientRole("ROLE_OWNER");
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setCreatedAt(LocalDateTime.now());
        notifications.save(n);
    }
}

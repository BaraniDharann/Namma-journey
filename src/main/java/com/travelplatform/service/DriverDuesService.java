package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.DriverDuesPayment;
import com.travelplatform.entity.DriverLedgerEntry;
import com.travelplatform.entity.Notification;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.DriverDuesPaymentRepository;
import com.travelplatform.repository.DriverLedgerRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static com.travelplatform.service.TelegramClient.escapeHtml;

/**
 * Independent drivers paying the commission they owe on cash rides, from the app (V24).
 *
 * <p>The driver pays the owner's UPI ID from their own UPI app (the app opens it with the amount
 * filled in), then submits the UTR. That is only a claim: the website cannot see the owner's bank
 * account, so the balance does not move until the owner confirms - on Telegram or the Payouts
 * page - and the confirmation writes the same COLLECTION ledger entry as "Record collection".
 */
@Service
public class DriverDuesService {

    private static final Logger log = LoggerFactory.getLogger(DriverDuesService.class);

    /** UPI references are 12 digits; some apps show longer alphanumeric ids, so allow those too. */
    private static final Pattern UTR = Pattern.compile("^[A-Za-z0-9]{6,64}$");

    private final DriverDuesPaymentRepository payments;
    private final DriverLedgerRepository ledger;
    private final DriverLedgerService ledgerService;
    private final DriverRepository drivers;
    private final NotificationRepository notifications;
    private final OwnerTelegramAlerts alerts;
    private final TelegramClient telegram;
    private final AfterCommitDispatcher dispatcher;
    private final TransactionTemplate tx;

    // Commission is paid online through a Razorpay Payment Link; payment_link.paid (handled in
    // RazorpayPaymentService) calls settleOnline. Field-injected so hand-built tests are unaffected.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RazorpayGateway gateway;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.config.RazorpayProperties razorpay;

    /** The note on a dues Payment Link that tells the webhook it is commission, not a fare. */
    public static final String LINK_KIND = "DRIVER_DUES";

    public DriverDuesService(DriverDuesPaymentRepository payments, DriverLedgerRepository ledger,
                             DriverLedgerService ledgerService, DriverRepository drivers,
                             NotificationRepository notifications, OwnerTelegramAlerts alerts,
                             TelegramClient telegram, AfterCommitDispatcher dispatcher,
                             TransactionTemplate tx) {
        this.payments = payments;
        this.ledger = ledger;
        this.ledgerService = ledgerService;
        this.drivers = drivers;
        this.notifications = notifications;
        this.alerts = alerts;
        this.telegram = telegram;
        this.dispatcher = dispatcher;
        this.tx = tx;
    }

    /**
     * @param owed    commission the driver owes now (0 when the platform owes them, or all settled)
     * @param pending already submitted, waiting for the owner
     * @param payable what is left to pay: owed less pending
     * @param payOnline whether Razorpay is set up, so the "Pay online" button can be offered
     */
    public record DuesView(BigDecimal owed, BigDecimal pending, BigDecimal payable, boolean payOnline,
                           List<PaymentRow> recent) {
    }

    public record PaymentRow(Long id, BigDecimal amount, String utr, String status,
                             LocalDateTime submittedAt, LocalDateTime decidedAt) {
        static PaymentRow of(DriverDuesPayment p) {
            return new PaymentRow(p.getId(), p.getAmount(), p.getUtr(), p.getStatus().name(), p.getSubmittedAt(), p.getDecidedAt());
        }
    }

    public record PendingRow(Long id, Long driverId, String driverName, String driverMobile, BigDecimal amount,
                             String utr, LocalDateTime submittedAt, BigDecimal driverOwes) {
    }

    @Transactional(readOnly = true)
    public DuesView view(Long driverId) {
        Driver driver = independent(driverId);
        BigDecimal owed = ledger.balanceOf(driver.getId()).negate().max(BigDecimal.ZERO);
        BigDecimal pending = payments.pendingTotal(driverId);
        BigDecimal payable = owed.subtract(pending).max(BigDecimal.ZERO);
        List<PaymentRow> recent = payments.findByDriverIdOrderBySubmittedAtDesc(driverId, PageRequest.of(0, 10))
                .stream().map(PaymentRow::of).toList();
        boolean online = razorpay != null && gateway != null && razorpay.isOperational();
        return new DuesView(owed, pending, payable, online, recent);
    }

    /**
     * "Pay online": a Razorpay Payment Link for everything the driver owes right now. The driver
     * pays it with any UPI app or card; payment_link.paid then calls {@link #settleOnline} and the
     * balance moves at once - no UTR to type, no owner checking a bank statement.
     *
     * <p>Not {@code @Transactional}: the Razorpay call must not hold a database connection.
     */
    public String payOnline(Long driverId) {
        if (razorpay == null || gateway == null || !razorpay.isOperational()) {
            throw new IllegalArgumentException("Online payment is not set up yet. Please try again later.");
        }
        BigDecimal payable = tx.execute(s -> {
            independent(driverId);
            BigDecimal owed = ledger.balanceOf(driverId).negate().max(BigDecimal.ZERO);
            return owed.subtract(payments.pendingTotal(driverId)).max(BigDecimal.ZERO);
        });
        if (payable == null || payable.signum() <= 0) {
            throw new IllegalArgumentException("You have no commission to pay right now");
        }
        long paise = payable.movePointRight(2).setScale(0, java.math.RoundingMode.UNNECESSARY).longValueExact();
        long expireBy = LocalDateTime.now().plusMinutes(30).atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        Map<String, String> notes = new java.util.LinkedHashMap<>();
        notes.put("kind", LINK_KIND);
        notes.put("driver_id", driverId.toString());
        String reference = "NJD-" + driverId + "-" + Long.toString(System.currentTimeMillis(), 36);
        return gateway.createPaymentLink(paise, reference, "Commission dues", expireBy, notes).shortUrl();
    }

    /**
     * Razorpay confirmed a dues Payment Link was paid: record it as a confirmed payment and move
     * the driver's balance. A repeat delivery of the same payment is a no-op.
     *
     * @return false when it could not be applied (logged for a person to refund or reconcile)
     */
    @Transactional
    public boolean settleOnline(Long driverId, long paidPaise, String razorpayPaymentId) {
        if (payments.utrInUse(razorpayPaymentId) || ledger.existsByReference(razorpayPaymentId)) {
            return true; // already recorded
        }
        Driver driver = drivers.findById(driverId).orElse(null);
        if (driver == null) {
            log.error("Razorpay dues payment {} for unknown driver {}: refund it from the dashboard", razorpayPaymentId, driverId);
            return false;
        }
        BigDecimal amount = BigDecimal.valueOf(paidPaise, 2);
        ledger.lockDriver(driverId);
        BigDecimal owed = ledger.balanceOf(driverId).negate().max(BigDecimal.ZERO);
        if (amount.compareTo(owed) > 0) {
            log.error("Razorpay dues payment {} of {} is more than driver {} owes ({}): refund the difference from the dashboard",
                    razorpayPaymentId, amount, driverId, owed);
            if (owed.signum() <= 0) {
                return false;
            }
            amount = owed;
        }
        String actor = "razorpay:" + razorpayPaymentId;
        LocalDateTime now = LocalDateTime.now();
        DriverDuesPayment p = new DriverDuesPayment();
        p.setDriverId(driverId);
        p.setAmount(amount);
        p.setUtr(razorpayPaymentId);
        p.setSubmittedAt(now);
        p.setStatus(DriverDuesPayment.Status.CONFIRMED);
        p.setDecidedAt(now);
        p.setDecidedBy(actor);
        payments.save(p);
        ledgerService.recordTransfer(driverId, DriverLedgerEntry.Type.COLLECTION, amount,
                razorpayPaymentId, "Paid online (Razorpay)", actor);

        String rupees = String.format(Locale.ROOT, "₹%.2f", amount);
        tellDriver(driver, "DUES_CONFIRMED", "Payment received",
                "Your commission payment of " + rupees + " was received. Thank you!");
        ownerNotice("DUES_PAID", "Driver paid commission online",
                driver.getName() + " paid " + rupees + " commission online (Razorpay " + razorpayPaymentId + ").");
        log.info("Driver {} paid {} commission online ({})", driverId, amount, razorpayPaymentId);
        return true;
    }

    /**
     * The driver says they paid. Refused when it is more than they still owe (counting claims
     * already waiting) or the UTR was used before. The driver's ledger is locked for the check, so
     * two submissions at once cannot both pass it.
     */
    @Transactional
    public DuesView submit(Long driverId, BigDecimal amount, String rawUtr) {
        Driver driver = independent(driverId);
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Enter the amount you paid, in rupees");
        }
        String utr = rawUtr == null ? "" : rawUtr.trim().toUpperCase(Locale.ROOT);
        if (!UTR.matcher(utr).matches()) {
            throw new IllegalArgumentException("Enter the UTR / UPI reference number from your payment app");
        }

        ledger.lockDriver(driverId);
        BigDecimal owed = ledger.balanceOf(driverId).negate().max(BigDecimal.ZERO);
        BigDecimal pending = payments.pendingTotal(driverId);
        BigDecimal payable = owed.subtract(pending).max(BigDecimal.ZERO);
        if (amount.compareTo(payable) > 0) {
            throw new IllegalArgumentException(pending.signum() > 0
                    ? String.format(Locale.ROOT, "You owe ₹%.2f, and ₹%.2f is already waiting for the owner to confirm", owed, pending)
                    : String.format(Locale.ROOT, "You owe only ₹%.2f", owed));
        }
        if (payments.utrInUse(utr) || ledger.existsByReference(utr)) {
            throw new IllegalArgumentException("This UTR has already been submitted");
        }

        DriverDuesPayment p = new DriverDuesPayment();
        p.setDriverId(driverId);
        p.setAmount(amount);
        p.setUtr(utr);
        p.setSubmittedAt(LocalDateTime.now());
        DriverDuesPayment saved = payments.save(p);

        ownerNotice("DUES_PAID", "Driver paid commission - please confirm",
                String.format(Locale.ROOT, "%s says they paid ₹%.2f (UTR %s). Check your bank, then confirm it on the Payouts page.",
                        driver.getName(), amount, utr));
        Long paymentId = saved.getId();
        alerts.send(ownerText(driver, saved, "⏳ <b>Check your bank</b>, then confirm."),
                List.of(List.of(
                        new TelegramClient.InlineButton("✅ Received", OwnerTelegramAlerts.DUES_CONFIRM + paymentId),
                        new TelegramClient.InlineButton("❌ Not received", OwnerTelegramAlerts.DUES_REJECT + paymentId))),
                messageId -> payments.setTelegramMessageId(paymentId, messageId));
        log.info("Driver {} submitted a commission payment of {} (UTR {})", driverId, amount, utr);
        return view(driverId);
    }

    /** The owner found the money: the ledger gets its COLLECTION and the driver's balance moves. */
    @Transactional
    public PaymentRow confirm(Long paymentId, String actor) {
        DriverDuesPayment p = pendingPayment(paymentId);
        if (payments.decide(paymentId, DriverDuesPayment.Status.CONFIRMED, LocalDateTime.now(), actor) == 0) {
            throw new IllegalArgumentException("This payment was already confirmed or rejected");
        }
        // Refuses (and rolls this back) if it is now more than owed - e.g. the owner had already
        // recorded the same transfer by hand.
        ledgerService.recordTransfer(p.getDriverId(), DriverLedgerEntry.Type.COLLECTION, p.getAmount(),
                p.getUtr(), "Paid in the app", actor);
        p.setStatus(DriverDuesPayment.Status.CONFIRMED);
        p.setDecidedAt(LocalDateTime.now());
        p.setDecidedBy(actor);

        Driver driver = drivers.findById(p.getDriverId()).orElse(null);
        String amount = String.format(Locale.ROOT, "₹%.2f", p.getAmount());
        tellDriver(driver, "DUES_CONFIRMED", "Payment received",
                "The owner confirmed your payment of " + amount + " (UTR " + p.getUtr() + "). Thank you!");
        if (driver != null) {
            alerts.rewrite(p.getTelegramMessageId(), ownerText(driver, p, "✅ <b>Received</b> - confirmed by " + escapeHtml(who(actor))));
        }
        log.info("Commission payment {} of driver {} confirmed by {}", paymentId, p.getDriverId(), actor);
        return PaymentRow.of(p);
    }

    /** The owner could not find the money. The balance is unchanged and the driver is told why. */
    @Transactional
    public PaymentRow reject(Long paymentId, String actor) {
        DriverDuesPayment p = pendingPayment(paymentId);
        if (payments.decide(paymentId, DriverDuesPayment.Status.REJECTED, LocalDateTime.now(), actor) == 0) {
            throw new IllegalArgumentException("This payment was already confirmed or rejected");
        }
        p.setStatus(DriverDuesPayment.Status.REJECTED);
        p.setDecidedAt(LocalDateTime.now());
        p.setDecidedBy(actor);

        Driver driver = drivers.findById(p.getDriverId()).orElse(null);
        String amount = String.format(Locale.ROOT, "₹%.2f", p.getAmount());
        tellDriver(driver, "DUES_REJECTED", "Payment not found",
                "The owner could not find your payment of " + amount + " (UTR " + p.getUtr() + "). "
                        + "Check the UTR in your payment app and submit it again, or contact the owner.");
        if (driver != null) {
            alerts.rewrite(p.getTelegramMessageId(), ownerText(driver, p, "❌ <b>Not received</b> - marked by " + escapeHtml(who(actor))));
        }
        log.info("Commission payment {} of driver {} rejected by {}", paymentId, p.getDriverId(), actor);
        return PaymentRow.of(p);
    }

    /** Claims waiting for the owner, oldest first, with what each driver owes now. */
    @Transactional(readOnly = true)
    public List<PendingRow> pending() {
        Map<Long, Driver> byId = new HashMap<>();
        return payments.findByStatusOrderBySubmittedAtAsc(DriverDuesPayment.Status.PENDING).stream().map(p -> {
            Driver d = byId.computeIfAbsent(p.getDriverId(), id -> drivers.findById(id).orElse(null));
            return new PendingRow(p.getId(), p.getDriverId(), d == null ? "Driver #" + p.getDriverId() : d.getName(),
                    d == null ? null : d.getMobile(), p.getAmount(), p.getUtr(), p.getSubmittedAt(),
                    ledger.balanceOf(p.getDriverId()).negate().max(BigDecimal.ZERO));
        }).toList();
    }

    /**
     * Received / Not received pressed in the owner's Telegram group (already checked to be that
     * group). The service rewrites the message itself, so nothing is appended here on success.
     */
    public OwnerTelegramAlerts.Outcome handleTelegram(String data, String actor) {
        boolean confirm = data.startsWith(OwnerTelegramAlerts.DUES_CONFIRM);
        long id;
        try {
            id = Long.parseLong(data.substring(confirm ? OwnerTelegramAlerts.DUES_CONFIRM.length() : OwnerTelegramAlerts.DUES_REJECT.length()));
        } catch (NumberFormatException e) {
            return new OwnerTelegramAlerts.Outcome("Unknown action", null, List.of());
        }
        // Through the template: a self-call would skip @Transactional, and the claim must not be
        // marked confirmed if writing its ledger entry then fails.
        try {
            if (confirm) {
                tx.executeWithoutResult(s -> confirm(id, "telegram:" + actor));
                return new OwnerTelegramAlerts.Outcome("Confirmed", null, List.of());
            }
            tx.executeWithoutResult(s -> reject(id, "telegram:" + actor));
            return new OwnerTelegramAlerts.Outcome("Marked not received", null, List.of());
        } catch (IllegalArgumentException | ResourceNotFoundException e) {
            return new OwnerTelegramAlerts.Outcome(e.getMessage().length() > 180 ? "Could not record it" : e.getMessage(),
                    "ℹ️ " + escapeHtml(e.getMessage()), List.of());
        }
    }

    // ---------------------------------------------------------------- helpers

    private Driver independent(Long driverId) {
        Driver driver = drivers.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
        if (driver.getFleet() != Driver.Fleet.INDEPENDENT) {
            throw new IllegalArgumentException("Owner-fleet drivers settle with the owner directly");
        }
        return driver;
    }

    private DriverDuesPayment pendingPayment(Long paymentId) {
        DriverDuesPayment p = payments.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (p.getStatus() != DriverDuesPayment.Status.PENDING) {
            throw new IllegalArgumentException("This payment was already confirmed or rejected");
        }
        return p;
    }

    private static String ownerText(Driver d, DriverDuesPayment p, String status) {
        return "💰 <b>Driver commission payment</b>\n\n"
                + "<b>Driver:</b> " + escapeHtml(d.getName()) + " — " + escapeHtml(d.getMobile()) + "\n"
                + "<b>Amount:</b> " + String.format(Locale.ROOT, "₹%.2f", p.getAmount()) + "\n"
                + "<b>UTR:</b> <code>" + escapeHtml(p.getUtr()) + "</code>\n\n"
                + status;
    }

    /** "telegram:Priya" / "owner:3" -> "Priya" / "owner". */
    private static String who(String actor) {
        if (actor == null) {
            return "owner";
        }
        return actor.startsWith("telegram:") ? actor.substring("telegram:".length()) : "owner";
    }

    private void tellDriver(Driver driver, String type, String title, String message) {
        if (driver == null) {
            return;
        }
        Notification n = new Notification();
        n.setRecipientId(driver.getId().toString());
        n.setRecipientRole("ROLE_DRIVER");
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setCreatedAt(LocalDateTime.now());
        notifications.save(n);
        String chat = driver.getTelegramChatId();
        if (chat != null && !chat.isBlank()) {
            String html = "<b>" + escapeHtml(title) + "</b>\n\n" + escapeHtml(message);
            dispatcher.dispatch("telegram", () -> telegram.sendMessage(chat, html, List.of()));
        }
    }

// Owner notices go to the platform owner's own feed (V27); "owner" only in hand-built tests.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TripRateService tripRates;

    private String platformFeed() {
        try {
            return tripRates == null ? "owner" : tripRates.platformOwnerId().toString();
        } catch (IllegalStateException noPlatformOwnerYet) {
            return "owner"; // a fresh database: the notice must not fail the work it reports
        }
    }

    private void ownerNotice(String type, String title, String message) {
        Notification n = new Notification();
        n.setRecipientId(platformFeed());
        n.setRecipientRole("ROLE_OWNER");
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setCreatedAt(LocalDateTime.now());
        notifications.save(n);
    }
}

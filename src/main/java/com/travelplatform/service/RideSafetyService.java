package com.travelplatform.service;

import com.travelplatform.config.SafetyProperties;
import com.travelplatform.dto.DriverLocationDTO;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.EmergencyContact;
import com.travelplatform.entity.Notification;
import com.travelplatform.entity.RideLocationPoint;
import com.travelplatform.entity.RideSafetyCheck;
import com.travelplatform.entity.RideSafetySession;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.User;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.EmergencyContactRepository;
import com.travelplatform.repository.NotificationRepository;
import com.travelplatform.repository.RideLocationPointRepository;
import com.travelplatform.repository.RideSafetyCheckRepository;
import com.travelplatform.repository.RideSafetySessionRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.travelplatform.service.TelegramClient.escapeHtml;

/**
 * Keeps an eye on passengers during a trip, over the free Telegram bot and in the app.
 *
 * <ul>
 *   <li><b>Ride updates</b> - driver accepted, trip started (with a share-trip link and a live
 *       map of the car), trip ended.
 *   <li><b>Safety checks</b> - "are you safe?" with Safe / Unsafe buttons: on a schedule, and
 *       sooner when something looks wrong (car stopped away from the drop, driver GPS gone
 *       quiet, trip running well past its estimate). Unanswered: asked again, then the owner
 *       is alerted.
 *   <li><b>SOS</b> - Unsafe, the in-app SOS button, or /sos in Telegram. Alerts the owner's
 *       safety chat with a live map, and the passenger's emergency contacts with the share link.
 * </ul>
 *
 * <p>What it deliberately does <em>not</em> do is promise police. Nothing here can dispatch
 * them, so the passenger is told plainly to call 112 if they are in danger, and that our team
 * and their contacts have been alerted - which is true.
 *
 * <p>Every outbound send is best-effort and after commit, through {@link AfterCommitDispatcher},
 * exactly like driver dispatch: the database rows (checks, in-app notifications) are the record,
 * and a Telegram outage never fails the trip action that triggered it.
 */
@Service
public class RideSafetyService {

    private static final Logger log = LoggerFactory.getLogger(RideSafetyService.class);

    /** Telegram's live-location maximum is a day; a city ride is ended long before this. */
    private static final int LIVE_PERIOD_SECONDS = 8 * 60 * 60;

    /** A share link keeps working this long after the trip ends, then shows nothing. */
    private static final Duration SHARE_GRACE = Duration.ofHours(1);

    private static final String SAFE_PREFIX = "safe:";
    private static final String UNSAFE_PREFIX = "unsafe:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RideSafetySessionRepository sessions;
    private final RideSafetyCheckRepository checks;
    private final RideLocationPointRepository trail;
    private final EmergencyContactRepository contacts;
    private final TravelBookingRepository bookings;
    private final UserRepository users;
    private final DriverRepository drivers;
    private final NotificationRepository notifications;
    private final LocationTrackingService locations;
    private final TelegramClient telegram;
    private final AfterCommitDispatcher dispatcher;
    private final SafetyProperties props;
    private final TransactionTemplate tx;

    /**
     * bookingId -> when this instance last handled a position for it. Positions arrive every
     * ~2 seconds per trip; only one per trail interval goes on to the database and Telegram.
     */
    private final ConcurrentHashMap<String, Long> lastPositionHandled = new ConcurrentHashMap<>();

    public RideSafetyService(RideSafetySessionRepository sessions,
                             RideSafetyCheckRepository checks,
                             RideLocationPointRepository trail,
                             EmergencyContactRepository contacts,
                             TravelBookingRepository bookings,
                             UserRepository users,
                             DriverRepository drivers,
                             NotificationRepository notifications,
                             LocationTrackingService locations,
                             TelegramClient telegram,
                             AfterCommitDispatcher dispatcher,
                             SafetyProperties props,
                             PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.checks = checks;
        this.trail = trail;
        this.contacts = contacts;
        this.bookings = bookings;
        this.users = users;
        this.drivers = drivers;
        this.notifications = notifications;
        this.locations = locations;
        this.telegram = telegram;
        this.dispatcher = dispatcher;
        this.props = props;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------------------------- ride events

    /** The driver took the trip: tell the passenger on Telegram who is coming. */
    public void onTripAccepted(TravelBooking booking, Driver driver) {
        if (!props.isEnabled()) {
            return;
        }
        String chatId = riderChat(booking.getUserId());
        if (chatId == null) {
            return;
        }
        StringBuilder text = new StringBuilder("✅ <b>Driver accepted your trip</b>\n\n");
        text.append("👤 <b>Driver:</b> ").append(escapeHtml(driver.getName())).append('\n');
        if (driver.getMobile() != null) {
            text.append("📞 <b>Phone:</b> ").append(escapeHtml(driver.getMobile())).append('\n');
        }
        String vehicle = vehicleLine(driver);
        if (!vehicle.isEmpty()) {
            text.append("🚗 <b>Vehicle:</b> ").append(escapeHtml(vehicle)).append('\n');
        }
        text.append("📍 ").append(escapeHtml(booking.getFromPlace()))
                .append(" → ").append(escapeHtml(booking.getToPlace())).append("\n\n");
        text.append("Check the vehicle number before you get in. Track the car in the app: ")
                .append(props.getPublicBaseUrl()).append("/user/bookings");
        dispatcher.dispatch("telegram", () -> telegram.sendMessage(chatId, text.toString(), List.of()));
    }

    /**
     * Start Trip: open the safety session, give the passenger their share link, and start the
     * live map. Runs inside the start-trip transaction, so a start that rolls back leaves no session.
     */
    public void onTripStarted(TravelBooking booking) {
        if (!props.isEnabled() || booking.getUserId() == null) {
            return;
        }
        RideSafetySession session = openSession(booking);
        if (!session.isActive()) {
            return;
        }

        String chatId = riderChat(booking.getUserId());
        if (chatId == null) {
            return;
        }
        String text = "🚕 <b>Your trip has started</b>\n\n"
                + escapeHtml(booking.getFromPlace()) + " → " + escapeHtml(booking.getToPlace()) + "\n\n"
                + "🔗 <b>Share your trip</b> with family - they can watch the car live:\n"
                + shareUrl(session) + "\n\n"
                + (autoChecks(booking)
                    ? "🛡 We will check on you during the ride. "
                    : "🛡 ")
                + "If you ever feel unsafe, send /sos here or press SOS in the app.";
        UUID bookingId = booking.getId();
        dispatcher.dispatch("telegram", () -> {
            telegram.sendMessage(chatId, text, List.of());
            DriverLocationDTO here = locations.getLocation(bookingId.toString());
            if (here != null) {
                startRiderLiveMap(bookingId, chatId, here.getLatitude(), here.getLongitude());
            }
        });
    }

    /** The session for this trip, created on first use. Must run inside a transaction. */
    private RideSafetySession openSession(TravelBooking booking) {
        RideSafetySession existing = sessions.findById(booking.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        RideSafetySession session = new RideSafetySession();
        session.setBookingId(booking.getId());
        session.setUserId(booking.getUserId());
        session.setShareToken(newToken());
        session.setStartedAt(now);
        session.setNextCheckAt(now.plus(props.getFirstCheckAfter()));
        return sessions.save(session);
    }

    /** The driver ended the trip: stop the live maps and close the session. Idempotent. */
    public void onTripEnded(TravelBooking booking) {
        RideSafetySession session = sessions.findById(booking.getId()).orElse(null);
        if (session == null || sessions.end(booking.getId(), LocalDateTime.now()) == 0) {
            return;
        }
        lastPositionHandled.remove(booking.getId().toString());

        String chatId = riderChat(booking.getUserId());
        Long userLive = session.getUserLiveMessageId();
        Long opsLive = session.getOpsLiveMessageId();
        String amount = booking.getTotalAmount() == null ? null
                : String.format(Locale.ROOT, "%.2f", booking.getTotalAmount());
        dispatcher.dispatch("telegram", () -> {
            if (chatId != null) {
                telegram.stopLiveLocation(chatId, userLive);
                telegram.sendMessage(chatId, "🏁 <b>Trip ended.</b> Thanks for riding with Namma Journey."
                        + (amount != null ? "\n\nAmount payable: ₹" + amount : ""), List.of());
            }
            if (props.hasOpsChat()) {
                telegram.stopLiveLocation(props.getOpsChatId(), opsLive);
            }
        });
    }

    // ---------------------------------------------------------------- location feed

    /**
     * One position from the driver's phone. Throttled in memory to one per trail interval per
     * trip, then: a trail row, and the passenger's (and, during an SOS, the owner's) live map
     * moved along. Never throws - it sits on the driver's location path.
     */
    public void onLocation(DriverLocationDTO dto) {
        if (!props.isEnabled() || dto == null || dto.getBookingId() == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        long intervalMs = props.getTrailInterval().toMillis();
        Long last = lastPositionHandled.get(dto.getBookingId());
        if (last != null && nowMs - last < intervalMs) {
            return;
        }
        lastPositionHandled.put(dto.getBookingId(), nowMs);

        try {
            UUID bookingId = UUID.fromString(dto.getBookingId());
            RideSafetySession session = sessions.findById(bookingId).orElse(null);
            if (session == null || !session.isActive()) {
                return; // on the way to pickup, or already over: nothing to record
            }
            LocalDateTime now = LocalDateTime.now();
            trail.save(new RideLocationPoint(bookingId, dto.getLatitude(), dto.getLongitude(), now));
            sessions.markPoint(bookingId, now);

            String riderChat = riderChat(session.getUserId());
            Long userLive = session.getUserLiveMessageId();
            Long opsLive = session.getOpsLiveMessageId();
            double lat = dto.getLatitude();
            double lon = dto.getLongitude();
            dispatcher.dispatch("telegram", () -> {
                if (riderChat != null) {
                    if (userLive == null) {
                        startRiderLiveMap(bookingId, riderChat, lat, lon);
                    } else {
                        telegram.editLiveLocation(riderChat, userLive, lat, lon);
                    }
                }
                if (opsLive != null && props.hasOpsChat()) {
                    telegram.editLiveLocation(props.getOpsChatId(), opsLive, lat, lon);
                }
            });
        } catch (Exception e) {
            log.warn("Safety location handling failed for booking {}: {}", dto.getBookingId(), e.toString());
        }
    }

    private void startRiderLiveMap(UUID bookingId, String chatId, double lat, double lon) {
        Long messageId = telegram.sendLiveLocation(chatId, lat, lon, LIVE_PERIOD_SECONDS);
        if (messageId != null) {
            sessions.setUserLiveMessageId(bookingId, messageId);
        }
    }

    // ---------------------------------------------------------------- monitor

    /**
     * Every 30 seconds: for each running trip, send what is due - a reminder, an escalation, a
     * scheduled check, or an early one because something looks wrong. One transaction per trip
     * so one bad row cannot stall the rest.
     */
    @Scheduled(fixedDelayString = "${app.safety.monitor-interval-ms:30000}", initialDelayString = "60000")
    public void monitor() {
        if (!props.isEnabled()) {
            return;
        }
        List<RideSafetySession> active;
        try {
            active = sessions.findByEndedAtIsNull();
        } catch (Exception e) {
            log.warn("Safety monitor could not list sessions: {}", e.toString());
            return;
        }
        for (RideSafetySession s : active) {
            try {
                tx.executeWithoutResult(status -> monitorOne(s.getBookingId()));
            } catch (Exception e) {
                log.warn("Safety monitor failed for booking {}: {}", s.getBookingId(), e.toString());
            }
        }
    }

    void monitorOne(UUID bookingId) {
        RideSafetySession session = sessions.findById(bookingId).orElse(null);
        if (session == null || !session.isActive()) {
            return;
        }
        TravelBooking booking = bookings.findById(bookingId).orElse(null);
        // Ended, paid, cancelled or deleted without passing through onTripEnded: just close it.
        if (booking == null || booking.getStatus() != TravelBooking.BookingStatus.STARTED
                || booking.getTripEndedAt() != null) {
            session.setEndedAt(LocalDateTime.now());
            sessions.save(session);
            lastPositionHandled.remove(bookingId.toString());
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        Optional<RideSafetyCheck> open = checks.findFirstByBookingIdAndResponseIsNullOrderBySentAtDesc(bookingId);
        if (open.isPresent()) {
            RideSafetyCheck check = open.get();
            if (!now.isBefore(check.getSentAt().plus(props.getEscalateAfter()))) {
                check.setResponse(RideSafetyCheck.Response.NO_RESPONSE);
                check.setRespondedAt(now);
                checks.save(check);
                alertNoResponse(session, booking, check);
            } else if (check.getRemindedAt() == null
                    && !now.isBefore(check.getSentAt().plus(props.getReminderAfter()))) {
                check.setRemindedAt(now);
                checks.save(check);
                sendCheck(booking, check, true);
            }
            return;
        }

        // During an SOS people are already on it; more "are you safe?" pings would only add noise.
        if (session.getSosAt() != null || !autoChecks(booking)) {
            return;
        }

        RideSafetyCheck.Reason reason = anomaly(session, booking, now);
        if (reason == null && !now.isBefore(session.getNextCheckAt())) {
            reason = RideSafetyCheck.Reason.SCHEDULED;
        }
        if (reason == null) {
            return;
        }
        RideSafetyCheck check = new RideSafetyCheck();
        check.setBookingId(bookingId);
        check.setReason(reason);
        check.setSentAt(now);
        check = checks.save(check);
        session.setLastCheckAt(now);
        session.setNextCheckAt(now.plus(props.getCheckInterval()));
        sessions.save(session);
        sendCheck(booking, check, false);
    }

    /**
     * What looks wrong right now, if anything. Rate-limited by the anomaly cooldown so a long
     * traffic jam asks once, not every run.
     */
    RideSafetyCheck.Reason anomaly(RideSafetySession session, TravelBooking booking, LocalDateTime now) {
        if (session.getLastCheckAt() != null
                && now.isBefore(session.getLastCheckAt().plus(props.getAnomalyCooldown()))) {
            return null;
        }

        // The driver's phone went quiet. Measured from the start when no point has arrived yet.
        LocalDateTime lastPoint = session.getLastPointAt() != null ? session.getLastPointAt() : session.getStartedAt();
        if (now.isAfter(lastPoint.plus(props.getGpsSilentFor()))) {
            return RideSafetyCheck.Reason.GPS_SILENT;
        }

        // Well past the estimate - asked once, the first time the line is crossed.
        Long estimate = booking.getEstimatedTimeMinutes();
        if (estimate != null && estimate > 0) {
            LocalDateTime overdueAt = session.getStartedAt()
                    .plusSeconds((long) (estimate * 60 * props.getOverdueFactor()));
            boolean askedSince = session.getLastCheckAt() != null && session.getLastCheckAt().isAfter(overdueAt);
            if (now.isAfter(overdueAt) && !askedSince) {
                return RideSafetyCheck.Reason.OVERDUE;
            }
        }

        if (stoppedAwayFromDrop(booking, now)) {
            return RideSafetyCheck.Reason.STOPPED;
        }
        return null;
    }

    /**
     * True when every trail point over the stopped window sits within a small radius, and that
     * spot is not the drop. Needs most of the window covered by points, so a trip that has only
     * just started, or a phone that reported twice, is not mistaken for a stop.
     */
    private boolean stoppedAwayFromDrop(TravelBooking booking, LocalDateTime now) {
        Duration window = props.getStoppedFor();
        List<RideLocationPoint> points = trail.findByBookingIdAndRecordedAtAfterOrderByRecordedAtAsc(
                booking.getId(), now.minus(window));
        long expected = Math.max(1, window.toSeconds() / Math.max(1, props.getTrailInterval().toSeconds()));
        if (points.size() < Math.max(3, expected * 6 / 10)) {
            return false;
        }
        RideLocationPoint latest = points.get(points.size() - 1);
        for (RideLocationPoint p : points) {
            if (meters(p.getLatitude(), p.getLongitude(), latest.getLatitude(), latest.getLongitude())
                    > props.getStoppedRadiusMeters()) {
                return false;
            }
        }
        if (booking.getToLat() != null && booking.getToLon() != null) {
            return meters(latest.getLatitude(), latest.getLongitude(), booking.getToLat(), booking.getToLon())
                    > props.getNearDropMeters();
        }
        return true;
    }

    /**
     * Automatic checks are for City rides - a cab across town. A multi-day outstation Trip
     * stops at hotels and meals for hours, and asking every 25 minutes for three days would
     * train the passenger to ignore the question. Those trips still get the share link, the
     * live map and SOS.
     */
    private static boolean autoChecks(TravelBooking booking) {
        return booking.getServiceMode() == TravelBooking.ServiceMode.CITY;
    }

    private void sendCheck(TravelBooking booking, RideSafetyCheck check, boolean reminder) {
        String question = switch (check.getReason()) {
            case SCHEDULED -> "Quick safety check on your ride.";
            case STOPPED -> "Your car has been stopped for a few minutes.";
            case GPS_SILENT -> "We have lost your driver's location for a few minutes.";
            case OVERDUE -> "Your ride is taking longer than expected.";
        };
        Notification n = new Notification();
        n.setRecipientId(booking.getUserId().toString());
        n.setRecipientRole("ROLE_USER");
        n.setType("SAFETY_CHECK");
        n.setTitle(reminder ? "Are you safe? Please answer" : "Are you safe?");
        n.setMessage(question + " Open My Bookings and tap Safe or Unsafe.");
        n.setBookingId(booking.getId().toString());
        n.setCreatedAt(LocalDateTime.now());
        notifications.save(n);

        String chatId = riderChat(booking.getUserId());
        if (chatId == null) {
            return;
        }
        String html = (reminder ? "⏰ <b>We haven't heard back - are you safe?</b>\n\n" : "🛡 <b>Are you safe?</b>\n\n")
                + escapeHtml(question) + "\n"
                + escapeHtml(booking.getFromPlace()) + " → " + escapeHtml(booking.getToPlace()) + "\n\n"
                + "Tap a button below.";
        List<List<TelegramClient.InlineButton>> buttons = List.of(List.of(
                new TelegramClient.InlineButton("✅ I'm safe", SAFE_PREFIX + check.getId()),
                new TelegramClient.InlineButton("🚨 Unsafe", UNSAFE_PREFIX + check.getId())));
        Long checkId = check.getId();
        dispatcher.dispatch("telegram", () -> {
            Long messageId = telegram.sendMessageForId(chatId, html, buttons);
            if (messageId != null) {
                checks.setTelegramMessageId(checkId, messageId);
            }
        });
    }

    // ---------------------------------------------------------------- answers and SOS

    public static boolean isSafetyCallback(String data) {
        return data != null && (data.startsWith(SAFE_PREFIX) || data.startsWith(UNSAFE_PREFIX));
    }

    /**
     * A Safe / Unsafe button press in Telegram. Identity comes from the Telegram account, never
     * from the payload: the check must belong to a trip of the passenger that account is linked to.
     *
     * @return the short toast to show on the button
     */
    public String answerFromTelegram(String telegramUserId, String data, Long messageId) {
        boolean safe = data.startsWith(SAFE_PREFIX);
        long checkId;
        try {
            checkId = Long.parseLong(data.substring(safe ? SAFE_PREFIX.length() : UNSAFE_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
        Optional<User> user = users.findByTelegramChatId(telegramUserId);
        if (user.isEmpty()) {
            return "This Telegram account is not linked to a passenger.";
        }
        Answer result = tx.execute(status -> {
            RideSafetyCheck check = checks.findById(checkId).orElse(null);
            if (check == null) {
                return Answer.NOT_FOUND;
            }
            return answer(user.get().getId(), check.getBookingId(), check, safe, "Telegram");
        });
        if (result == Answer.NOT_FOUND) {
            return "This check is no longer active.";
        }
        String chatId = user.get().getTelegramChatId();
        if (safe) {
            telegram.editMessageText(chatId, messageId, "✅ <b>Thanks - glad you're safe.</b> Have a good ride.");
            return "Thanks!";
        }
        telegram.editMessageText(chatId, messageId, "🚨 <b>You reported feeling unsafe.</b> Help is being alerted - see below.");
        return "Alert sent";
    }

    /**
     * The passenger's answer to the open check on their trip, from the app.
     *
     * @throws AccessDeniedException if the trip is not theirs
     */
    public Answer answerInApp(UUID userId, UUID bookingId, boolean safe) {
        return tx.execute(status -> {
            RideSafetyCheck check = checks.findFirstByBookingIdAndResponseIsNullOrderBySentAtDesc(bookingId).orElse(null);
            if (check == null) {
                requireOwnTrip(userId, bookingId);
                // No open question: Safe is a no-op, Unsafe is still an SOS.
                if (!safe) {
                    sosLocked(userId, bookingId, "Passenger pressed Unsafe in the app");
                    return Answer.UNSAFE_RECORDED;
                }
                return Answer.SAFE_RECORDED;
            }
            return answer(userId, bookingId, check, safe, "the app");
        });
    }

    private Answer answer(UUID userId, UUID bookingId, RideSafetyCheck check, boolean safe, String via) {
        TravelBooking booking = requireOwnTrip(userId, bookingId);
        LocalDateTime now = LocalDateTime.now();
        RideSafetyCheck.Response previous = check.getResponse();

        if (safe) {
            if (previous == RideSafetyCheck.Response.SAFE) {
                return Answer.SAFE_RECORDED;
            }
            check.setResponse(RideSafetyCheck.Response.SAFE);
            check.setRespondedAt(now);
            checks.save(check);
            // The owner was already told this passenger went silent; tell them it is resolved.
            if (previous == RideSafetyCheck.Response.NO_RESPONSE) {
                opsAlert(booking, "✅ <b>Update:</b> " + escapeHtml(booking.getUserName())
                        + " has now answered <b>Safe</b> (via " + via + ").", "SAFETY_RESOLVED",
                        "Passenger answered safe", booking.getUserName() + " has now answered Safe.");
            }
            return Answer.SAFE_RECORDED;
        }

        check.setResponse(RideSafetyCheck.Response.UNSAFE);
        check.setRespondedAt(now);
        checks.save(check);
        sosLocked(userId, bookingId, "Passenger tapped Unsafe (" + via + ")");
        return Answer.UNSAFE_RECORDED;
    }

    /** SOS from the in-app button. */
    public void sos(UUID userId, UUID bookingId) {
        tx.executeWithoutResult(status -> sosLocked(userId, bookingId, "Passenger pressed SOS in the app"));
    }

    /** /sos typed to the bot: the passenger's current trip, if they have one. */
    public String sosFromTelegram(String telegramUserId) {
        Optional<User> user = users.findByTelegramChatId(telegramUserId);
        if (user.isEmpty()) {
            return "❌ This Telegram account is not linked to a passenger. "
                    + "If you are in danger, call <b>112</b> now.";
        }
        Optional<RideSafetySession> session = sessions.findFirstByUserIdAndEndedAtIsNullOrderByStartedAtDesc(user.get().getId());
        if (session.isEmpty()) {
            return "You have no trip in progress right now. If you are in danger, call <b>112</b> now.";
        }
        tx.executeWithoutResult(status ->
                sosLocked(user.get().getId(), session.get().getBookingId(), "Passenger sent /sos on Telegram"));
        return null; // the SOS itself replies in the chat
    }

    /**
     * Raises (or re-raises) the alarm. First time: owner + contacts + passenger. Again while it
     * is already raised: the owner only, so a frightened passenger pressing repeatedly does not
     * flood their family with copies.
     */
    private void sosLocked(UUID userId, UUID bookingId, String trigger) {
        TravelBooking booking = requireOwnTrip(userId, bookingId);
        // SOS before Start Trip (waiting at pickup, or the driver never pressed Start) still has
        // to work, and needs a session for its share link.
        RideSafetySession session = openSession(booking);
        boolean first = session.getSosAt() == null;
        LocalDateTime now = LocalDateTime.now();
        if (first) {
            session.setSosAt(now);
            sessions.save(session);
        }
        // Any question still waiting is answered by this.
        checks.findFirstByBookingIdAndResponseIsNullOrderBySentAtDesc(bookingId).ifPresent(c -> {
            c.setResponse(RideSafetyCheck.Response.UNSAFE);
            c.setRespondedAt(now);
            checks.save(c);
        });

        Driver driver = booking.getDriverId() == null ? null : drivers.findById(booking.getDriverId()).orElse(null);
        User passenger = users.findById(userId).orElse(null);
        DriverLocationDTO here = currentPosition(bookingId);
        String share = shareUrl(session);
        String mapLink = here == null ? null : mapsUrl(here.getLatitude(), here.getLongitude());

        log.warn("SOS on booking {} by passenger {}: {}", bookingId, userId, trigger);

        // 1. The owner: in-app always, Telegram group when configured, with a live map.
        StringBuilder ops = new StringBuilder(first ? "🚨🚨 <b>SOS - passenger needs help</b>\n\n" : "🚨 <b>SOS pressed again</b>\n\n");
        ops.append("<b>Why:</b> ").append(escapeHtml(trigger)).append('\n');
        ops.append("<b>Passenger:</b> ").append(escapeHtml(booking.getUserName()));
        if (passenger != null && passenger.getPhone() != null) {
            ops.append(" — ").append(escapeHtml(passenger.getPhone()));
        } else if (booking.getUserPhone() != null) {
            ops.append(" — ").append(escapeHtml(booking.getUserPhone()));
        }
        ops.append('\n');
        if (driver != null) {
            ops.append("<b>Driver:</b> ").append(escapeHtml(driver.getName()));
            if (driver.getMobile() != null) {
                ops.append(" — ").append(escapeHtml(driver.getMobile()));
            }
            String vehicle = vehicleLine(driver);
            if (!vehicle.isEmpty()) {
                ops.append(" — ").append(escapeHtml(vehicle));
            }
            ops.append('\n');
        }
        ops.append("<b>Trip:</b> ").append(escapeHtml(booking.getFromPlace()))
                .append(" → ").append(escapeHtml(booking.getToPlace())).append('\n');
        ops.append("<b>Live:</b> ").append(share).append('\n');
        if (mapLink != null) {
            ops.append("<b>Last position:</b> ").append(mapLink).append('\n');
        }
        ops.append("\nCall the passenger now. If they cannot talk or confirm danger, call 112.");
        opsAlert(booking, ops.toString(), "SOS",
                first ? "SOS - passenger needs help" : "SOS pressed again",
                booking.getUserName() + " (" + trigger + ") on " + booking.getFromPlace() + " → "
                        + booking.getToPlace() + ". Call them now. Live: " + share);
        if (first && here != null && props.hasOpsChat()) {
            double lat = here.getLatitude();
            double lon = here.getLongitude();
            dispatcher.dispatch("telegram", () -> {
                Long id = telegram.sendLiveLocation(props.getOpsChatId(), lat, lon, LIVE_PERIOD_SECONDS);
                if (id != null) {
                    sessions.setOpsLiveMessageId(bookingId, id);
                }
            });
        }

        // 2. The passenger: honest about what happens now. No promise of police - we cannot send them.
        Notification toPassenger = new Notification();
        toPassenger.setRecipientId(userId.toString());
        toPassenger.setRecipientRole("ROLE_USER");
        toPassenger.setType("SOS_ACK");
        toPassenger.setTitle("Help is being alerted");
        toPassenger.setMessage("Our safety team and your emergency contacts have been alerted with your live location. "
                + "If you are in danger, call 112 now.");
        toPassenger.setBookingId(bookingId.toString());
        toPassenger.setCreatedAt(now);
        notifications.save(toPassenger);
        String riderChat = riderChat(userId);
        if (riderChat != null) {
            String reply = "🚨 <b>We've alerted our safety team</b>"
                    + (first ? " and your emergency contacts" : "")
                    + " with your live location.\n\n"
                    + "📞 <b>If you are in danger, call 112 now</b> (police / emergency).\n\n"
                    + "Stay on a call with someone you trust if you can. Your trip link: " + share;
            dispatcher.dispatch("telegram", () -> telegram.sendMessage(riderChat, reply, List.of()));
        }

        // 3. Emergency contacts, first time only.
        if (first) {
            String name = escapeHtml(booking.getUserName());
            String alert = "🚨 <b>" + name + " may be unsafe</b>\n\n"
                    + name + " pressed SOS on a Namma Journey ride and listed you as an emergency contact.\n\n"
                    + "<b>Trip:</b> " + escapeHtml(booking.getFromPlace()) + " → " + escapeHtml(booking.getToPlace()) + "\n"
                    + (driver != null ? "<b>Driver:</b> " + escapeHtml(driver.getName())
                            + (vehicleLine(driver).isEmpty() ? "" : " — " + escapeHtml(vehicleLine(driver))) + "\n" : "")
                    + "<b>Watch live:</b> " + share + "\n"
                    + (mapLink != null ? "<b>Last position:</b> " + mapLink + "\n" : "")
                    + "\nPlease call " + name + " now. If you cannot reach them and believe they are in danger, call 112.";
            List<String> contactChats = new ArrayList<>();
            for (EmergencyContact c : contacts.findByUserIdOrderByCreatedAtAsc(userId)) {
                if (c.getTelegramChatId() != null) {
                    contactChats.add(c.getTelegramChatId());
                }
            }
            if (!contactChats.isEmpty()) {
                dispatcher.dispatch("telegram", () -> contactChats.forEach(chat -> telegram.sendMessage(chat, alert, List.of())));
            }
        }
    }

    /** Asked twice, no answer: the owner decides what to do. Contacts are not alarmed for silence alone. */
    private void alertNoResponse(RideSafetySession session, TravelBooking booking, RideSafetyCheck check) {
        Driver driver = booking.getDriverId() == null ? null : drivers.findById(booking.getDriverId()).orElse(null);
        DriverLocationDTO here = currentPosition(booking.getId());
        String html = "⚠️ <b>Passenger not answering safety check</b>\n\n"
                + "<b>Asked because:</b> " + check.getReason().name().toLowerCase(Locale.ROOT).replace('_', ' ') + "\n"
                + "<b>Passenger:</b> " + escapeHtml(booking.getUserName())
                + (booking.getUserPhone() != null ? " — " + escapeHtml(booking.getUserPhone()) : "") + "\n"
                + (driver != null ? "<b>Driver:</b> " + escapeHtml(driver.getName())
                        + (driver.getMobile() != null ? " — " + escapeHtml(driver.getMobile()) : "") + "\n" : "")
                + "<b>Trip:</b> " + escapeHtml(booking.getFromPlace()) + " → " + escapeHtml(booking.getToPlace()) + "\n"
                + "<b>Live:</b> " + shareUrl(session) + "\n"
                + (here != null ? "<b>Last position:</b> " + mapsUrl(here.getLatitude(), here.getLongitude()) + "\n" : "")
                + "\nPlease call the passenger to check on them.";
        opsAlert(booking, html, "SAFETY_NO_RESPONSE", "Passenger not answering safety check",
                booking.getUserName() + " did not answer two safety checks on " + booking.getFromPlace()
                        + " → " + booking.getToPlace() + ". Call them. Live: " + shareUrl(session));

        String riderChat = riderChat(booking.getUserId());
        if (riderChat != null) {
            dispatcher.dispatch("telegram", () -> telegram.sendMessage(riderChat,
                    "We didn't hear back from you, so our team has been asked to check on you. "
                            + "If you are fine, you can ignore this. If you are in danger, call <b>112</b>.", List.of()));
        }
    }

    /** In-app notification to the owner, plus the Telegram safety group when configured. */
    private void opsAlert(TravelBooking booking, String html, String type, String title, String plain) {
        Notification n = new Notification();
        n.setRecipientId("owner");
        n.setRecipientRole("ROLE_OWNER");
        n.setType(type);
        n.setTitle(title);
        n.setMessage(plain);
        n.setBookingId(booking.getId().toString());
        n.setCreatedAt(LocalDateTime.now());
        notifications.save(n);
        if (props.hasOpsChat()) {
            String chat = props.getOpsChatId();
            dispatcher.dispatch("telegram", () -> telegram.sendMessage(chat, html, List.of()));
        }
    }

    // ---------------------------------------------------------------- reads

    /** What the passenger's trip card needs: is a question waiting, is SOS on, the share link. */
    public SafetyView view(UUID userId, UUID bookingId) {
        requireOwnTrip(userId, bookingId);
        RideSafetySession session = sessions.findById(bookingId).orElse(null);
        Optional<RideSafetyCheck> open = session == null || !session.isActive()
                ? Optional.empty()
                : checks.findFirstByBookingIdAndResponseIsNullOrderBySentAtDesc(bookingId);
        return new SafetyView(
                session != null && session.isActive(),
                session == null ? null : shareUrl(session),
                open.map(c -> c.getReason().name()).orElse(null),
                session != null && session.getSosAt() != null);
    }

    /**
     * The public share-trip page. Holding the token is the permission. Shows the car, never the
     * passenger's phone; stops answering an hour after the trip ends.
     */
    public Optional<SharedTrip> sharedTrip(String token) {
        if (token == null || token.isBlank() || token.length() > 64) {
            return Optional.empty();
        }
        return sessions.findByShareToken(token)
                .filter(s -> s.getEndedAt() == null || s.getEndedAt().plus(SHARE_GRACE).isAfter(LocalDateTime.now()))
                .flatMap(s -> bookings.findById(s.getBookingId()).map(b -> {
                    Driver d = b.getDriverId() == null ? null : drivers.findById(b.getDriverId()).orElse(null);
                    DriverLocationDTO here = s.isActive() ? currentPosition(b.getId()) : null;
                    return new SharedTrip(
                            firstName(b.getUserName()),
                            b.getFromPlace(), b.getToPlace(),
                            b.getFromLat(), b.getFromLon(), b.getToLat(), b.getToLon(),
                            d == null ? null : d.getName(),
                            d == null ? null : vehicleLine(d),
                            here == null ? null : here.getLatitude(),
                            here == null ? null : here.getLongitude(),
                            here == null ? null : here.getTimestamp(),
                            !s.isActive(),
                            s.getSosAt() != null);
                }));
    }

    // ---------------------------------------------------------------- helpers

    private TravelBooking requireOwnTrip(UUID userId, UUID bookingId) {
        TravelBooking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        if (booking.getUserId() == null || !booking.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not your trip");
        }
        return booking;
    }

    private String riderChat(UUID userId) {
        if (userId == null) {
            return null;
        }
        return users.findById(userId)
                .map(User::getTelegramChatId)
                .filter(id -> !id.isBlank())
                .orElse(null);
    }

    /** Live position from the tracking store, else the last trail point. */
    private DriverLocationDTO currentPosition(UUID bookingId) {
        DriverLocationDTO live = locations.getLocation(bookingId.toString());
        if (live != null) {
            return live;
        }
        return trail.findFirstByBookingIdOrderByRecordedAtDesc(bookingId)
                .map(p -> new DriverLocationDTO(null, bookingId.toString(), p.getLatitude(), p.getLongitude(), 0,
                        p.getRecordedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
                .orElse(null);
    }

    private String shareUrl(RideSafetySession session) {
        return props.getPublicBaseUrl() + "/trip/" + session.getShareToken();
    }

    private static String mapsUrl(double lat, double lon) {
        return String.format(Locale.ROOT, "https://maps.google.com/?q=%.6f,%.6f", lat, lon);
    }

    private static String vehicleLine(Driver d) {
        String model = d.getVehicleModel() == null ? "" : d.getVehicleModel().trim();
        String number = d.getVehicleNumber() == null ? "" : d.getVehicleNumber().trim();
        return (model + " " + number).trim();
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return "Passenger";
        }
        return name.trim().split("\\s+")[0];
    }

    private static String newToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** Great-circle distance in metres. */
    static double meters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    public enum Answer {
        SAFE_RECORDED,
        UNSAFE_RECORDED,
        NOT_FOUND
    }

    /** @param pendingCheck reason of the question waiting for an answer, or null */
    public record SafetyView(boolean active, String shareUrl, String pendingCheck, boolean sosActive) {
    }

    public record SharedTrip(String passengerFirstName,
                             String fromPlace, String toPlace,
                             Double fromLat, Double fromLon, Double toLat, Double toLon,
                             String driverName, String vehicle,
                             Double latitude, Double longitude, Long positionAt,
                             boolean ended, boolean sos) {
    }
}

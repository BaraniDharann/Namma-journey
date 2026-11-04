package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusBookingViews.SlabView;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusTicket;
import com.travelplatform.entity.BusTicketPassenger;
import com.travelplatform.entity.BusTrip;
import com.travelplatform.entity.User;
import com.travelplatform.outbox.Outbox;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteStopRepository;
import com.travelplatform.repository.BusTicketPassengerRepository;
import com.travelplatform.repository.BusTripRepository;
import com.travelplatform.repository.UserRepository;
import com.travelplatform.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Messages to bus customers: email to the ticket's contact address and Telegram to the chats linked
 * to the customer (and to the contact and extra phone numbers, when they belong to someone who
 * linked Telegram). No SMS. Everything goes through the outbox in the caller's transaction, so a
 * message is sent only for a change that committed.
 */
@Component
public class BusNotifier {

    private static final Logger log = LoggerFactory.getLogger(BusNotifier.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    private final EmailService email;
    private final Outbox outbox;
    private final UserRepository users;
    private final BusTripRepository trips;
    private final BusRepository buses;
    private final BusRouteStopRepository stops;
    private final BusRoutePointRepository points;
    private final BusPlaceRepository places;
    private final BusTicketPassengerRepository passengers;

    public BusNotifier(EmailService email, Outbox outbox, UserRepository users, BusTripRepository trips,
                       BusRepository buses, BusRouteStopRepository stops, BusRoutePointRepository points,
                       BusPlaceRepository places, BusTicketPassengerRepository passengers) {
        this.email = email;
        this.outbox = outbox;
        this.users = users;
        this.trips = trips;
        this.buses = buses;
        this.stops = stops;
        this.points = points;
        this.places = places;
        this.passengers = passengers;
    }

    /** What every message about a ticket needs, loaded once. */
    private record Facts(BusTicket ticket, BusTrip trip, Bus bus, String from, String to, BusRoutePoint board,
                         BusRoutePoint drop, LocalDateTime boardingAt, List<BusTicketPassenger> passengers) {

        String journey() {
            return from + " to " + to;
        }
    }

    /** The ticket is paid and confirmed: details and the cancellation table. */
    public void confirmed(BusTicket ticket) {
        Facts f = facts(ticket);
        if (f == null) {
            return;
        }
        StringBuilder html = new StringBuilder();
        html.append("<h2 style='color:#0f766e;'>Your bus ticket is confirmed</h2>")
                .append(summaryHtml(f))
                .append("<p><strong>Total paid:</strong> Rs. ").append(ticket.getTotalAmount())
                .append(" (fare Rs. ").append(ticket.getFareTotal()).append(" + GST Rs. ").append(ticket.getGstTotal()).append(")</p>")
                .append(slabsHtml(f))
                .append("<p>Show this ticket number to the conductor when you board.</p>");
        sendEmail(f, "Bus ticket " + ticket.getTicketNo() + " confirmed: " + f.journey(), html.toString());

        StringBuilder tg = new StringBuilder("✅ <b>Bus ticket confirmed</b>\n\n");
        tg.append(summaryText(f))
                .append("💳 <b>Paid:</b> Rs. ").append(ticket.getTotalAmount()).append("\n\n")
                .append("<b>Cancellation charges</b>\n").append(slabsText(f));
        sendTelegram(f, tg.toString());
    }

    /** About two hours before boarding: the bus, the crew's phones and where to board. */
    public void reminder(BusTicket ticket) {
        Facts f = facts(ticket);
        if (f == null) {
            return;
        }
        StringBuilder html = new StringBuilder("<h2 style='color:#0f766e;'>Your bus leaves soon</h2>")
                .append(summaryHtml(f))
                .append("<p><strong>Bus number:</strong> ").append(esc(f.bus == null ? "" : f.bus.getRegistrationNumber())).append("</p>")
                .append(crewHtml(f.trip))
                .append("<p>Please be at the boarding point 15 minutes early.</p>");
        sendEmail(f, "Reminder: your bus " + ticket.getTicketNo() + " leaves at "
                + (f.boardingAt == null ? "" : f.boardingAt.format(WHEN)), html.toString());

        StringBuilder tg = new StringBuilder("🚌 <b>Your bus leaves soon</b>\n\n").append(summaryText(f));
        tg.append("🔢 <b>Bus number:</b> ").append(esc(f.bus == null ? "" : f.bus.getRegistrationNumber())).append('\n');
        if (f.trip.getDriverPhone() != null) {
            tg.append("📞 <b>Driver:</b> ").append(esc(nz(f.trip.getDriverName()))).append(' ').append(esc(f.trip.getDriverPhone())).append('\n');
        }
        if (f.trip.getConductorPhone() != null) {
            tg.append("📞 <b>Conductor:</b> ").append(esc(nz(f.trip.getConductorName()))).append(' ').append(esc(f.trip.getConductorPhone())).append('\n');
        }
        tg.append("\nPlease be at the boarding point 15 minutes early.");
        sendTelegram(f, tg.toString());
    }

    /** The money arrived after the seats were sold to someone else: the whole amount goes back. */
    public void paidTooLate(BusTicket ticket, BigDecimal refund) {
        Facts f = facts(ticket);
        if (f == null) {
            return;
        }
        String text = "Your payment for ticket " + ticket.getTicketNo() + " (" + f.journey() + ") arrived after the seat hold ran out"
                + " and the seats were taken by someone else. We are refunding Rs. " + refund
                + " in full; it reaches your account in 5-7 working days.";
        sendEmail(f, "Refund for bus ticket " + ticket.getTicketNo(), "<h2>Seats no longer available</h2><p>" + esc(text) + "</p>");
        sendTelegram(f, "↩️ <b>Payment refunded</b>\n\n" + esc(text));
    }

    /** The customer cancelled some or all seats. */
    public void cancelled(BusTicket ticket, Collection<String> seats, BigDecimal refund) {
        Facts f = facts(ticket);
        if (f == null) {
            return;
        }
        String text = "Seat" + (seats.size() == 1 ? " " : "s ") + String.join(", ", seats) + " on ticket " + ticket.getTicketNo()
                + " (" + f.journey() + ", " + (f.boardingAt == null ? "" : f.boardingAt.format(WHEN)) + ") "
                + (seats.size() == 1 ? "is" : "are") + " cancelled. "
                + (refund.signum() > 0 ? "Rs. " + refund + " is being refunded to your account (5-7 working days)."
                : "No refund is due under the cancellation policy.");
        sendEmail(f, "Bus ticket " + ticket.getTicketNo() + ": cancellation", "<h2>Cancellation done</h2><p>" + esc(text) + "</p>");
        sendTelegram(f, "❌ <b>Seats cancelled</b>\n\n" + esc(text));
    }

    /** The owner cancelled the trip: every passenger is told, with their refund. */
    public void tripCancelled(BusTicket ticket, String reason, BigDecimal refund) {
        Facts f = facts(ticket);
        if (f == null) {
            return;
        }
        String text = "The operator cancelled the bus for ticket " + ticket.getTicketNo() + " (" + f.journey() + ", "
                + (f.boardingAt == null ? "" : f.boardingAt.format(WHEN)) + "). Reason: " + nz(reason) + ". "
                + (refund.signum() > 0 ? "Your full fare with GST, Rs. " + refund + ", is being refunded (5-7 working days)."
                : "");
        sendEmail(f, "Bus cancelled: ticket " + ticket.getTicketNo(), "<h2>Your bus was cancelled</h2><p>" + esc(text) + "</p>");
        sendTelegram(f, "⚠️ <b>Bus cancelled</b>\n\n" + esc(text));
    }

    // ---------------------------------------------------------------------------------------------

    private Facts facts(BusTicket ticket) {
        try {
            BusTrip trip = trips.findById(ticket.getTripId()).orElse(null);
            if (trip == null) {
                return null;
            }
            Bus bus = buses.findById(trip.getBusId()).orElse(null);
            BusRouteParts p = BusRouteParts.load(trip.getRouteId(), stops, points, places);
            BusRoutePoint board = p.pointById().get(ticket.getBoardingPointId());
            BusRoutePoint drop = p.pointById().get(ticket.getDroppingPointId());
            return new Facts(ticket, trip, bus, p.placeName(ticket.getFromSeq()), p.placeName(ticket.getToSeq()), board, drop,
                    board == null ? null : board.at(trip.getTravelDate()), passengers.findByTicketIdOrderByIdAsc(ticket.getId()));
        } catch (RuntimeException e) {
            log.warn("Could not compose a message for bus ticket {}: {}", ticket.getTicketNo(), e.toString());
            return null;
        }
    }

    private String summaryHtml(Facts f) {
        StringBuilder s = new StringBuilder("<div style='background:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;'>");
        s.append("<p><strong>Ticket:</strong> ").append(esc(f.ticket.getTicketNo())).append("</p>")
                .append("<p><strong>Journey:</strong> ").append(esc(f.journey())).append(", ")
                .append(f.trip.getTravelDate().format(DAY)).append("</p>")
                .append("<p><strong>Bus:</strong> ").append(esc(f.bus == null ? "" : f.bus.getName())).append("</p>");
        if (f.board != null) {
            s.append("<p><strong>Boarding:</strong> ").append(esc(f.board.getName())).append(", ")
                    .append(f.boardingAt.format(WHEN));
            if (f.board.getAddress() != null) {
                s.append("<br>").append(esc(f.board.getAddress()));
            }
            if (f.board.getLandmark() != null) {
                s.append(" (near ").append(esc(f.board.getLandmark())).append(')');
            }
            s.append("</p>");
        }
        if (f.drop != null) {
            s.append("<p><strong>Dropping:</strong> ").append(esc(f.drop.getName())).append(", ")
                    .append(f.drop.at(f.trip.getTravelDate()).format(WHEN)).append("</p>");
        }
        s.append("<p><strong>Seats:</strong> ");
        List<String> seats = new ArrayList<>();
        for (BusTicketPassenger p : f.passengers) {
            if (p.getStatus() == BusTicketPassenger.Status.CONFIRMED) {
                seats.add(esc(p.getSeatLabel()) + " " + esc(p.getName()) + " (" + p.getAge() + ", " + p.getGender() + ")");
            }
        }
        s.append(String.join("; ", seats)).append("</p></div>");
        return s.toString();
    }

    private String summaryText(Facts f) {
        StringBuilder s = new StringBuilder();
        s.append("🎫 <b>Ticket:</b> ").append(esc(f.ticket.getTicketNo())).append('\n')
                .append("📍 ").append(esc(f.journey())).append('\n')
                .append("🚌 ").append(esc(f.bus == null ? "" : f.bus.getName())).append('\n');
        if (f.board != null) {
            s.append("🕘 <b>Board:</b> ").append(esc(f.board.getName())).append(", ").append(f.boardingAt.format(WHEN)).append('\n');
            if (f.board.getAddress() != null) {
                s.append("    ").append(esc(f.board.getAddress())).append('\n');
            }
        }
        List<String> seats = f.passengers.stream().filter(p -> p.getStatus() == BusTicketPassenger.Status.CONFIRMED)
                .map(BusTicketPassenger::getSeatLabel).toList();
        s.append("💺 <b>Seats:</b> ").append(esc(String.join(", ", seats))).append('\n');
        return s.toString();
    }

    private String slabsHtml(Facts f) {
        StringBuilder s = new StringBuilder("<p><strong>Cancellation charges</strong> (on the fare; GST is refunded in full)</p>"
                + "<table style='border-collapse:collapse;'>");
        for (String row : slabRows(f)) {
            s.append("<tr><td style='padding:4px 10px;border:1px solid #ddd;'>").append(esc(row)).append("</td></tr>");
        }
        return s.append("</table>").toString();
    }

    private String slabsText(Facts f) {
        StringBuilder s = new StringBuilder();
        for (String row : slabRows(f)) {
            s.append("• ").append(esc(row)).append('\n');
        }
        return s.toString();
    }

    /** "Until Sat 8 Nov, 21:30: 10% of the fare", ..., "After Mon 10 Nov, 09:30: 100% of the fare". */
    private static List<String> slabRows(Facts f) {
        List<SlabView> slabs = BusFares.slabViews(BusCancellationPolicy.fromJson(f.ticket.getCancellationPolicy()), f.boardingAt);
        List<String> rows = new ArrayList<>();
        LocalDateTime previous = null;
        for (SlabView s : slabs) {
            String when = s.until() != null ? "Until " + s.until().format(WHEN)
                    : previous != null ? "After " + previous.format(WHEN) : "Before boarding";
            rows.add(when + ": " + s.percent() + "% of the fare");
            previous = s.until();
        }
        return rows;
    }

    private static String crewHtml(BusTrip trip) {
        StringBuilder s = new StringBuilder();
        if (trip.getDriverPhone() != null) {
            s.append("<p><strong>Driver:</strong> ").append(esc(nz(trip.getDriverName()))).append(' ')
                    .append(esc(trip.getDriverPhone())).append("</p>");
        }
        if (trip.getConductorPhone() != null) {
            s.append("<p><strong>Conductor:</strong> ").append(esc(nz(trip.getConductorName()))).append(' ')
                    .append(esc(trip.getConductorPhone())).append("</p>");
        }
        return s.toString();
    }

    private void sendEmail(Facts f, String subject, String bodyHtml) {
        String to = f.ticket.getContactEmail();
        if (to == null || to.isBlank()) {
            to = users.findById(f.ticket.getUserId()).map(User::getEmail).orElse(null);
        }
        if (to != null && !to.isBlank()) {
            email.sendBusTicketEmail(to, subject, bodyHtml);
        }
    }

    /** To the customer's own chat, and to the contact and extra phones' owners when they linked Telegram. */
    private void sendTelegram(Facts f, String html) {
        Set<String> chats = new LinkedHashSet<>();
        users.findById(f.ticket.getUserId()).map(User::getTelegramChatId).ifPresent(chats::add);
        chatOfPhone(f.ticket.getContactPhone()).ifPresent(chats::add);
        chatOfPhone(f.ticket.getExtraPhone()).ifPresent(chats::add);
        for (String chat : chats) {
            if (chat != null && !chat.isBlank()) {
                outbox.enqueue(BusTelegramJobHandler.TYPE, new BusTelegramJobHandler.Message(chat, html));
            }
        }
    }

    private Optional<String> chatOfPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        for (String form : List.of(phone, "+91" + phone)) {
            try {
                Optional<String> chat = users.findByPhone(form).map(User::getTelegramChatId);
                if (chat.isPresent()) {
                    return chat;
                }
            } catch (RuntimeException e) {
                // Two accounts on one number: nobody to pick, so no message to that number.
                log.debug("Phone {} is not unique among users: {}", form, e.toString());
            }
        }
        return Optional.empty();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }
}

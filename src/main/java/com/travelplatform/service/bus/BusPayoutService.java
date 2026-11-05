package com.travelplatform.service.bus;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.dto.bus.BusBookingViews.PayoutView;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusOwnerPayout;
import com.travelplatform.entity.BusRoute;
import com.travelplatform.entity.BusTicket;
import com.travelplatform.entity.BusTicketPassenger;
import com.travelplatform.entity.BusTrip;
import com.travelplatform.entity.Owner;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.BusOwnerPayoutRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusRouteRepository;
import com.travelplatform.repository.BusTicketPassengerRepository;
import com.travelplatform.repository.BusTicketRepository;
import com.travelplatform.repository.BusTripRepository;
import com.travelplatform.repository.OwnerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What the platform owes bus owners: one payout per completed trip, made the next morning, which
 * the admin pays by UPI or bank and marks paid with the UTR.
 *
 * <p>gross = fares (without GST) of passengers who travelled + cancellation charges kept on the
 * trip's tickets; commission = gross × the commission percent copied onto each ticket when it was
 * bought (worked out per ticket, so a rate change mid-season is honoured); net = gross − commission.
 * Rounded half-up to paise. A trip that earned nothing gets no payout row.
 */
@Service
public class BusPayoutService {

    private static final Logger log = LoggerFactory.getLogger(BusPayoutService.class);

    /** Trips completed longer ago than this are not looked at again (a zero-earning trip never gets a row). */
    static final int LOOKBACK_DAYS = 30;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Pattern UTR = Pattern.compile("^[A-Za-z0-9-]{6,40}$");

    private final BusOwnerPayoutRepository payouts;
    private final BusTripRepository trips;
    private final BusRouteRepository routes;
    private final BusRepository buses;
    private final BusTicketRepository tickets;
    private final BusTicketPassengerRepository passengers;
    private final OwnerRepository owners;
    private final TransactionTemplate newTx;
    private final OwnerScope ownerScope = new OwnerScope();
    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public BusPayoutService(BusOwnerPayoutRepository payouts, BusTripRepository trips, BusRouteRepository routes,
                            BusRepository buses, BusTicketRepository tickets, BusTicketPassengerRepository passengers,
                            OwnerRepository owners, PlatformTransactionManager txManager) {
        this.payouts = payouts;
        this.trips = trips;
        this.routes = routes;
        this.buses = buses;
        this.tickets = tickets;
        this.passengers = passengers;
        this.owners = owners;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Daily (BusJobs): a payout for each completed trip that has none. Safe to run again. */
    public int createPayouts() {
        int made = 0;
        for (Long tripId : trips.findCompletedWithoutPayout(LocalDateTime.now(clock).minusDays(LOOKBACK_DAYS))) {
            try {
                Boolean created = newTx.execute(s -> createFor(tripId));
                made += Boolean.TRUE.equals(created) ? 1 : 0;
            } catch (DataIntegrityViolationException alreadyThere) {
                // Another instance made it a moment ago (uq_bus_owner_payouts_trip).
            } catch (RuntimeException e) {
                log.warn("Could not create the payout for bus trip {}: {}", tripId, e.toString());
            }
        }
        if (made > 0) {
            log.info("Bus payouts: {} created", made);
        }
        return made;
    }

    boolean createFor(Long tripId) {
        BusTrip trip = trips.findById(tripId).orElse(null);
        if (trip == null || trip.getStatus() != BusTrip.Status.COMPLETED || payouts.findByTripId(tripId).isPresent()) {
            return false;
        }
        List<BusTicket> all = tickets.findByTripIdAndStatusIn(tripId, EnumSet.allOf(BusTicket.Status.class));
        Map<UUID, List<BusTicketPassenger>> paxByTicket = all.isEmpty() ? Map.of()
                : passengers.findByTicketIdIn(all.stream().map(BusTicket::getId).toList()).stream()
                        .collect(Collectors.groupingBy(BusTicketPassenger::getTicketId));
        BigDecimal travelled = BigDecimal.ZERO;
        BigDecimal kept = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        Set<BigDecimal> percents = new HashSet<>();
        for (BusTicket t : all) {
            BigDecimal ticketTravelled = BigDecimal.ZERO;
            BigDecimal ticketKept = BigDecimal.ZERO;
            for (BusTicketPassenger p : paxByTicket.getOrDefault(t.getId(), List.of())) {
                if (t.getStatus() == BusTicket.Status.COMPLETED && p.getStatus() == BusTicketPassenger.Status.CONFIRMED) {
                    ticketTravelled = ticketTravelled.add(p.getFare());
                }
                if (p.getCancelCharge() != null) {
                    ticketKept = ticketKept.add(p.getCancelCharge());
                }
            }
            BigDecimal ticketGross = ticketTravelled.add(ticketKept);
            if (ticketGross.signum() > 0) {
                percents.add(t.getCommissionPercent().stripTrailingZeros());
                commission = commission.add(ticketGross.multiply(t.getCommissionPercent()).divide(HUNDRED, 8, RoundingMode.HALF_UP));
            }
            travelled = travelled.add(ticketTravelled);
            kept = kept.add(ticketKept);
        }
        BigDecimal gross = BusFares.money(travelled.add(kept));
        if (gross.signum() <= 0) {
            return false;
        }
        BigDecimal commissionPaise = BusFares.money(commission);
        BigDecimal percent = percents.size() == 1 ? percents.iterator().next().setScale(2, RoundingMode.HALF_UP)
                : commissionPaise.multiply(HUNDRED).divide(gross, 2, RoundingMode.HALF_UP);
        BusOwnerPayout p = new BusOwnerPayout();
        p.setOwnerId(trip.getOwnerId());
        p.setTripId(tripId);
        p.setFaresTravelled(BusFares.money(travelled));
        p.setChargesKept(BusFares.money(kept));
        p.setGross(gross);
        p.setCommissionPercent(percent);
        p.setCommission(commissionPaise);
        p.setNet(gross.subtract(commissionPaise));
        p.setStatus(BusOwnerPayout.Status.DUE);
        payouts.saveAndFlush(p);
        return true;
    }

    /** The calling owner's payouts, newest first; bank details are left out. */
    @Transactional(readOnly = true)
    public List<PayoutView> mine() {
        return views(payouts.findByOwnerIdOrderByCreatedAtDesc(ownerScope.ownerId()), false);
    }

    /** All payouts (DUE oldest first, so the longest-waiting is on top), or every payout newest first. */
    @Transactional(readOnly = true)
    public List<PayoutView> all(String status) {
        if (status == null || status.isBlank()) {
            return views(payouts.findAllByOrderByCreatedAtDesc(), true);
        }
        BusOwnerPayout.Status s;
        try {
            s = BusOwnerPayout.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status must be DUE or PAID");
        }
        return views(payouts.findByStatusOrderByCreatedAtAsc(s), true);
    }

    /** The admin paid the owner: the UTR of the transfer is required. */
    @Transactional
    public PayoutView markPaid(Long id, String utr, Long adminId) {
        String ref = utr == null ? "" : utr.trim();
        if (!UTR.matcher(ref).matches()) {
            throw new IllegalArgumentException("Enter the UTR / reference of the transfer (6 to 40 letters or digits)");
        }
        BusOwnerPayout p = payouts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payout not found"));
        if (p.getStatus() != BusOwnerPayout.Status.DUE) {
            throw new ConflictException("This payout is already marked paid");
        }
        p.setStatus(BusOwnerPayout.Status.PAID);
        p.setUtr(ref.toUpperCase(Locale.ROOT));
        p.setPaidAt(LocalDateTime.now(clock));
        p.setPaidByAdminId(adminId);
        return views(List.of(payouts.save(p)), true).get(0);
    }

    private List<PayoutView> views(List<BusOwnerPayout> rows, boolean withBank) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, BusTrip> tripById = byId(trips.findAllById(ids(rows, BusOwnerPayout::getTripId)), BusTrip::getId);
        Map<Long, BusRoute> routeById = byId(routes.findAllById(ids(tripById.values(), BusTrip::getRouteId)), BusRoute::getId);
        Map<Long, Bus> busById = byId(buses.findAllById(ids(tripById.values(), BusTrip::getBusId)), Bus::getId);
        Map<Long, Owner> ownerById = byId(owners.findAllById(ids(rows, BusOwnerPayout::getOwnerId)), Owner::getId);
        return rows.stream().map(p -> {
            BusTrip t = tripById.get(p.getTripId());
            BusRoute r = t == null ? null : routeById.get(t.getRouteId());
            Bus b = t == null ? null : busById.get(t.getBusId());
            Owner o = ownerById.get(p.getOwnerId());
            boolean bank = withBank && o != null;
            return new PayoutView(p.getId(), p.getTripId(), t == null ? null : t.getTravelDate(),
                    r == null ? null : r.getName(), b == null ? null : b.getName(), p.getFaresTravelled(),
                    p.getChargesKept(), p.getGross(), p.getCommissionPercent(), p.getCommission(), p.getNet(),
                    p.getStatus().name(), p.getUtr(), p.getPaidAt(), p.getCreatedAt(), p.getOwnerId(),
                    o == null ? null : o.getOwnerCode(), o == null ? null : o.getTravelsName(),
                    bank ? o.getUpiId() : null, bank ? o.getBankAccountHolder() : null,
                    bank ? o.getBankAccountNumber() : null, bank ? o.getBankIfsc() : null,
                    bank ? o.getBankName() : null);
        }).toList();
    }

    private static <T, K> Map<K, T> byId(Collection<T> rows, Function<T, K> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    private static <T> Set<Long> ids(Collection<T> rows, Function<T, Long> id) {
        return rows.stream().map(id).collect(Collectors.toSet());
    }
}

package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusBookingViews.PassengerView;
import com.travelplatform.dto.bus.BusBookingViews.RefundView;
import com.travelplatform.dto.bus.BusBookingViews.ReviewView;
import com.travelplatform.dto.bus.BusBookingViews.TicketView;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusRefund;
import com.travelplatform.entity.BusReview;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusSeat;
import com.travelplatform.entity.BusTicket;
import com.travelplatform.entity.BusTicketPassenger;
import com.travelplatform.entity.BusTrip;
import com.travelplatform.entity.Owner;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRefundRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusReviewRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteStopRepository;
import com.travelplatform.repository.BusSeatRepository;
import com.travelplatform.repository.BusTicketPassengerRepository;
import com.travelplatform.repository.BusTripRepository;
import com.travelplatform.repository.OwnerRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the customer's Ticket JSON (spec appendix) for one ticket or a whole list, loading the
 * trips, buses, routes, passengers, refunds and reviews of all of them in a handful of queries.
 */
@Component
public class BusTicketViews {

    /** Tickets whose passengers are (or were) going to travel: they can be cancelled before boarding. */
    static final Set<BusTicket.Status> CANCELLABLE = Set.of(BusTicket.Status.CONFIRMED, BusTicket.Status.PARTIALLY_CANCELLED);

    private final BusTripRepository trips;
    private final BusRepository buses;
    private final BusSeatRepository seats;
    private final BusRouteStopRepository stops;
    private final BusRoutePointRepository points;
    private final BusPlaceRepository places;
    private final OwnerRepository owners;
    private final BusTicketPassengerRepository passengers;
    private final BusRefundRepository refunds;
    private final BusReviewRepository reviews;
    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public BusTicketViews(BusTripRepository trips, BusRepository buses, BusSeatRepository seats,
                          BusRouteStopRepository stops, BusRoutePointRepository points, BusPlaceRepository places,
                          OwnerRepository owners, BusTicketPassengerRepository passengers, BusRefundRepository refunds,
                          BusReviewRepository reviews) {
        this.trips = trips;
        this.buses = buses;
        this.seats = seats;
        this.stops = stops;
        this.points = points;
        this.places = places;
        this.owners = owners;
        this.passengers = passengers;
        this.refunds = refunds;
        this.reviews = reviews;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public TicketView one(BusTicket ticket) {
        return many(List.of(ticket)).get(0);
    }

    public List<TicketView> many(List<BusTicket> tickets) {
        if (tickets.isEmpty()) {
            return List.of();
        }
        Map<Long, BusTrip> tripById = byId(trips.findAllById(ids(tickets, BusTicket::getTripId)), BusTrip::getId);
        Map<Long, Bus> busById = byId(buses.findAllById(ids(tripById.values(), BusTrip::getBusId)), Bus::getId);
        Map<Long, Owner> ownerById = byId(owners.findAllById(ids(tickets, BusTicket::getOwnerId)), Owner::getId);
        Map<Long, List<BusSeat>> seatsByBus = seats.findByBusIdIn(busById.keySet()).stream()
                .collect(Collectors.groupingBy(BusSeat::getBusId));
        Map<Long, BusRouteParts> parts = BusRouteParts.load(ids(tripById.values(), BusTrip::getRouteId), stops, points, places);
        Set<UUID> ticketIds = tickets.stream().map(BusTicket::getId).collect(Collectors.toSet());
        Map<UUID, List<BusTicketPassenger>> paxByTicket = passengers.findByTicketIdIn(ticketIds).stream()
                .sorted(Comparator.comparing(BusTicketPassenger::getId))
                .collect(Collectors.groupingBy(BusTicketPassenger::getTicketId));
        Map<UUID, List<BusRefund>> refundsByTicket = refunds.findByTicketIdIn(ticketIds).stream()
                .sorted(Comparator.comparing(BusRefund::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(BusRefund::getTicketId));
        Map<UUID, BusReview> reviewByTicket = byId(reviews.findByTicketIdIn(ticketIds), BusReview::getTicketId);
        LocalDateTime now = LocalDateTime.now(clock);

        return tickets.stream().map(t -> {
            BusTrip trip = tripById.get(t.getTripId());
            Bus bus = trip == null ? null : busById.get(trip.getBusId());
            BusRouteParts p = trip == null ? null : parts.get(trip.getRouteId());
            BusRoutePoint board = p == null ? null : p.pointById().get(t.getBoardingPointId());
            BusRoutePoint drop = p == null ? null : p.pointById().get(t.getDroppingPointId());
            LocalDateTime boardingAt = board == null || trip == null ? null : board.at(trip.getTravelDate());
            List<BusTicketPassenger> pax = paxByTicket.getOrDefault(t.getId(), List.of());
            BusReview review = reviewByTicket.get(t.getId());
            Owner owner = ownerById.get(t.getOwnerId());
            boolean canCancel = CANCELLABLE.contains(t.getStatus()) && trip != null
                    && (trip.getStatus() == BusTrip.Status.SCHEDULED || trip.getStatus() == BusTrip.Status.DEPARTED)
                    && boardingAt != null && now.isBefore(boardingAt)
                    && pax.stream().anyMatch(x -> x.getStatus() == BusTicketPassenger.Status.CONFIRMED);
            return new TicketView(t.getId(), t.getTicketNo(), t.getStatus().name(), t.getTripId(),
                    trip == null ? null : trip.getStatus().name(), owner == null ? null : owner.getTravelsName(),
                    bus == null ? null : bus.getName(),
                    bus == null ? null : BusLayoutRules.typeLabel(bus.isAc(), seatsByBus.getOrDefault(bus.getId(), List.of())),
                    trip == null ? null : trip.getTravelDate(),
                    p == null ? null : p.placeName(t.getFromSeq()), p == null ? null : p.placeName(t.getToSeq()),
                    trip == null ? null : BusRouteParts.detail(board, trip.getTravelDate()),
                    trip == null ? null : BusRouteParts.detail(drop, trip.getTravelDate()),
                    t.getHoldExpiresAt(), t.getFareTotal(), t.getGstPercent(), t.getGstTotal(), t.getTotalAmount(),
                    t.getContactPhone(), t.getContactEmail(), t.getExtraPhone(),
                    pax.stream().map(x -> new PassengerView(x.getSeatLabel(), x.getSeatClass().name(), x.getName(), x.getAge(),
                            x.getGender().name(), x.getFare(), x.getGst(), x.getStatus().name(), x.getCancelCharge(),
                            x.getRefundAmount())).toList(),
                    BusFares.slabViews(BusCancellationPolicy.fromJson(t.getCancellationPolicy()), boardingAt),
                    refundsByTicket.getOrDefault(t.getId(), List.of()).stream()
                            .map(r -> new RefundView(r.getId(), r.getAmount(), r.getStatus().name(), r.getCreatedAt())).toList(),
                    bus == null ? null : bus.getRegistrationNumber(),
                    trip == null ? null : trip.getDriverName(), trip == null ? null : trip.getDriverPhone(),
                    trip == null ? null : trip.getConductorName(), trip == null ? null : trip.getConductorPhone(),
                    canCancel, t.getStatus() == BusTicket.Status.COMPLETED && review == null,
                    review == null ? null : new ReviewView(review.getRating(), review.getComment()),
                    t.getCreatedAt(), t.getConfirmedAt());
        }).toList();
    }

    private static <T, K> Map<K, T> byId(Collection<T> rows, Function<T, K> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    private static <T> Set<Long> ids(Collection<T> rows, Function<T, Long> id) {
        return rows.stream().map(id).collect(Collectors.toSet());
    }
}

package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.DriverLedgerEntry;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerLedgerEntry;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.OwnerLedgerRepository;
import com.travelplatform.repository.OwnerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The money between the platform and travels owners for City rides done in the owners' City cars
 * (V35). It works like the independent drivers' ledger, with the owner in the driver's place: a
 * ride paid online owes the owner the fare less the city's commission, a ride paid in cash leaves
 * the fare with the owner's driver, so the owner owes the commission. The owner pays their own
 * driver outside the app. The platform owner's own cars are not in it: that money is the platform's.
 */
@Service
public class OwnerLedgerService {

    private static final Logger log = LoggerFactory.getLogger(OwnerLedgerService.class);
    private static final Pattern UTR = Pattern.compile("^[A-Za-z0-9]{6,64}$");

    private final OwnerLedgerRepository ledger;
    private final OwnerRepository owners;
    private final CityService cityService;

    @Autowired(required = false)
    private TripRateService tripRates;

    public OwnerLedgerService(OwnerLedgerRepository ledger, OwnerRepository owners, CityService cityService) {
        this.ledger = ledger;
        this.owners = owners;
        this.cityService = cityService;
    }

    void setTripRates(TripRateService tripRates) {
        this.tripRates = tripRates;
    }

    public record EarningsView(BigDecimal balance, List<OwnerLedgerEntry> recent) {
    }

    public record PayoutRow(Long ownerId, String ownerCode, String travelsName, String phone, String upiId,
                            BigDecimal balance, long rides) {
    }

    /** Called from DriverLedgerService when a City ride by an owner-fleet driver is done and paid. Idempotent. */
    @Transactional
    public void recordCompletedRide(TravelBooking booking, Payment fare, Driver driver) {
        if (booking.getServiceMode() != TravelBooking.ServiceMode.CITY || fare == null || fare.getAmount() == null
                || driver == null || driver.getFleet() != Driver.Fleet.OWNER_FLEET || driver.getOwnerId() == null) {
            return;
        }
        if (tripRates != null && Objects.equals(driver.getOwnerId(), tripRates.platformOwnerId())) {
            return; // the platform's own fleet: nothing owed between the platform and itself
        }
        boolean cash = fare.getPaymentMethod() == Payment.PaymentMethod.CASH;
        DriverLedgerEntry.Type type = cash ? DriverLedgerEntry.Type.CASH_COMMISSION : DriverLedgerEntry.Type.RIDE_EARNING;
        if (ledger.existsByBookingIdAndType(booking.getId(), type)) {
            return;
        }
        BigDecimal percent = cityService.commissionPercent(booking.getCityId());
        BigDecimal commission = DriverLedgerService.commission(fare.getAmount(), percent);

        OwnerLedgerEntry entry = new OwnerLedgerEntry();
        entry.setOwnerId(driver.getOwnerId());
        entry.setBookingId(booking.getId());
        entry.setDriverId(driver.getId());
        entry.setVehicleId(booking.getVehicleId());
        entry.setType(type);
        entry.setFare(fare.getAmount());
        entry.setCommissionPercent(percent);
        entry.setCommission(commission);
        entry.setAmount(cash ? commission.negate() : fare.getAmount().subtract(commission));
        entry.setCreatedBy("system");
        entry.setCreatedAt(LocalDateTime.now());
        ledger.save(entry);
    }

    /** The owner's own City-ride balance and latest entries. */
    @Transactional(readOnly = true)
    public EarningsView earnings(Long ownerId) {
        return new EarningsView(ledger.balanceOf(ownerId),
                ledger.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId, PageRequest.of(0, 50)));
    }

    /** Every owner with City-ride history and their balance, largest owed to an owner first (platform screen). */
    @Transactional(readOnly = true)
    public List<PayoutRow> payoutSummary() {
        List<PayoutRow> rows = new ArrayList<>();
        for (Object[] r : ledger.balances()) {
            Long ownerId = (Long) r[0];
            BigDecimal balance = (BigDecimal) r[1];
            long rides = r[2] == null ? 0 : ((Number) r[2]).longValue();
            Owner o = owners.findById(ownerId).orElse(null);
            rows.add(new PayoutRow(ownerId, o == null ? null : o.getOwnerCode(), o == null ? null : o.getTravelsName(),
                    o == null ? null : o.getPhone(), o == null ? null : o.getUpiId(), balance, rides));
        }
        rows.sort(Comparator.comparing(PayoutRow::balance).reversed());
        return rows;
    }

    /**
     * Records money the platform sent an owner (PAYOUT) or received from one (COLLECTION), with
     * its UTR; never more than is owed, so a typo cannot flip a balance.
     */
    @Transactional
    public OwnerLedgerEntry recordTransfer(Long ownerId, DriverLedgerEntry.Type type, BigDecimal amount,
                                           String reference, String note, String actor) {
        if (type != DriverLedgerEntry.Type.PAYOUT && type != DriverLedgerEntry.Type.COLLECTION) {
            throw new IllegalArgumentException("Record a PAYOUT or a COLLECTION");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Enter an amount in rupees, up to 2 decimals");
        }
        String ref = reference == null ? "" : reference.trim().toUpperCase(Locale.ROOT);
        if (!UTR.matcher(ref).matches()) {
            throw new IllegalArgumentException("Enter the UPI reference (UTR) from your payment app");
        }
        Owner owner = owners.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        if (ledger.existsByReference(ref)) {
            throw new IllegalArgumentException("This UTR is already recorded");
        }
        ledger.lockOwner(ownerId);
        BigDecimal balance = ledger.balanceOf(ownerId);
        String name = owner.getTravelsName() == null ? owner.getOwnerCode() : owner.getTravelsName();
        if (type == DriverLedgerEntry.Type.PAYOUT && amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "%s is owed only %.2f", name, balance.max(BigDecimal.ZERO)));
        }
        if (type == DriverLedgerEntry.Type.COLLECTION && amount.compareTo(balance.negate()) > 0) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "%s owes only %.2f", name, balance.negate().max(BigDecimal.ZERO)));
        }
        OwnerLedgerEntry entry = new OwnerLedgerEntry();
        entry.setOwnerId(ownerId);
        entry.setType(type);
        entry.setAmount(type == DriverLedgerEntry.Type.PAYOUT ? amount.negate() : amount);
        entry.setReference(ref);
        entry.setNote(note == null || note.isBlank() ? null : note.trim().substring(0, Math.min(255, note.trim().length())));
        entry.setCreatedBy(actor);
        entry.setCreatedAt(LocalDateTime.now());
        log.info("{} of {} recorded for owner {} by {} (ref {})", type, amount, ownerId, actor, ref);
        return ledger.save(entry);
    }
}

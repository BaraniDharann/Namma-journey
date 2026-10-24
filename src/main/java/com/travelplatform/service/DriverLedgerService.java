package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.DriverLedgerEntry;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.DriverLedgerRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.security.FieldEncryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The money between the platform and its independent drivers (V21).
 *
 * <p>Only City rides by INDEPENDENT drivers enter the ledger: owner-fleet drivers are the
 * owner's own staff and are paid outside the app. A ride paid to the platform (online, or UPI to
 * the owner) owes the driver the fare less the city's commission; a ride paid in cash leaves the
 * fare with the driver, who then owes the commission. The owner settles balances by UPI from
 * their own app and records each transfer here with its UTR.
 */
@Service
public class DriverLedgerService {

    private static final Logger log = LoggerFactory.getLogger(DriverLedgerService.class);

    /** name@handle, as UPI IDs are: letters, digits, dot, dash, underscore before the @. */
    static final Pattern UPI_ID = Pattern.compile("^[a-zA-Z0-9._-]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,63}$");
    private static final Pattern UTR = Pattern.compile("^[A-Za-z0-9]{6,64}$");

    private final DriverLedgerRepository ledger;
    private final DriverRepository driverRepository;
    private final CityService cityService;

    public DriverLedgerService(DriverLedgerRepository ledger, DriverRepository driverRepository, CityService cityService) {
        this.ledger = ledger;
        this.driverRepository = driverRepository;
        this.cityService = cityService;
    }

    public record EarningsView(BigDecimal balance, boolean upiSet, String upiMasked, List<DriverLedgerEntry> recent) {
    }

    public record PayoutRow(Long driverId, String name, String mobile, String upiId, BigDecimal balance) {
    }

    /**
     * Called in the transaction that completes a ride with its fare settled. Idempotent: the
     * second call for the same ride finds its entry (and the unique index backs that up).
     */
    @Transactional
    public void recordCompletedRide(TravelBooking booking, Payment fare) {
        if (booking.getServiceMode() != TravelBooking.ServiceMode.CITY || booking.getDriverId() == null
                || fare == null || fare.getAmount() == null) {
            return;
        }
        Driver driver = driverRepository.findById(booking.getDriverId()).orElse(null);
        if (driver == null || driver.getFleet() != Driver.Fleet.INDEPENDENT) {
            return;
        }
        boolean cash = fare.getPaymentMethod() == Payment.PaymentMethod.CASH;
        DriverLedgerEntry.Type type = cash ? DriverLedgerEntry.Type.CASH_COMMISSION : DriverLedgerEntry.Type.RIDE_EARNING;
        if (ledger.existsByBookingIdAndType(booking.getId(), type)) {
            return;
        }

        BigDecimal percent = cityService.commissionPercent(booking.getCityId());
        BigDecimal commission = commission(fare.getAmount(), percent);

        DriverLedgerEntry entry = new DriverLedgerEntry();
        entry.setDriverId(driver.getId());
        entry.setBookingId(booking.getId());
        entry.setType(type);
        entry.setFare(fare.getAmount());
        entry.setCommissionPercent(percent);
        entry.setCommission(commission);
        entry.setAmount(cash ? commission.negate() : fare.getAmount().subtract(commission));
        entry.setCreatedBy("system");
        entry.setCreatedAt(LocalDateTime.now());
        ledger.save(entry);
    }

    static BigDecimal commission(BigDecimal fare, BigDecimal percent) {
        return fare.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Records money the owner sent a driver (PAYOUT) or received from one (COLLECTION). Never
     * more than is owed, so a typo cannot flip a balance; the driver's ledger is locked for the
     * check-and-insert so two owners recording at once cannot both pass it.
     */
    @Transactional
    public DriverLedgerEntry recordTransfer(Long driverId, DriverLedgerEntry.Type type, BigDecimal amount,
                                           String reference, String note, String actor) {
        if (type != DriverLedgerEntry.Type.PAYOUT && type != DriverLedgerEntry.Type.COLLECTION) {
            throw new IllegalArgumentException("Record a PAYOUT or a COLLECTION");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Enter an amount in rupees, up to 2 decimals");
        }
        String ref = reference == null ? "" : reference.trim();
        if (!UTR.matcher(ref).matches()) {
            throw new IllegalArgumentException("Enter the UPI reference (UTR) from your payment app");
        }
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("Driver not found"));
        if (driver.getFleet() != Driver.Fleet.INDEPENDENT) {
            throw new IllegalArgumentException("Only independent drivers are paid through the app");
        }

        ledger.lockDriver(driverId);
        BigDecimal balance = ledger.balanceOf(driverId);
        if (type == DriverLedgerEntry.Type.PAYOUT && amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "%s is owed only %.2f", driver.getName(), balance.max(BigDecimal.ZERO)));
        }
        if (type == DriverLedgerEntry.Type.COLLECTION && amount.compareTo(balance.negate()) > 0) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "%s owes only %.2f", driver.getName(), balance.negate().max(BigDecimal.ZERO)));
        }

        DriverLedgerEntry entry = new DriverLedgerEntry();
        entry.setDriverId(driverId);
        entry.setType(type);
        entry.setAmount(type == DriverLedgerEntry.Type.PAYOUT ? amount.negate() : amount);
        entry.setReference(ref.toUpperCase(Locale.ROOT));
        entry.setNote(note == null || note.isBlank() ? null : note.trim().substring(0, Math.min(255, note.trim().length())));
        entry.setCreatedBy(actor);
        entry.setCreatedAt(LocalDateTime.now());
        log.info("{} of {} recorded for driver {} by {} (ref {})", type, amount, driverId, actor, entry.getReference());
        return ledger.save(entry);
    }

    @Transactional(readOnly = true)
    public EarningsView earnings(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("Driver not found"));
        String upi = driver.getPayoutUpiId();
        return new EarningsView(ledger.balanceOf(driverId), upi != null, mask(upi),
                ledger.findByDriverIdOrderByCreatedAtDescIdDesc(driverId, PageRequest.of(0, 50)));
    }

    @Transactional
    public EarningsView setPayoutUpi(Long driverId, String upiId) {
        String upi = upiId == null ? "" : upiId.trim();
        if (!UPI_ID.matcher(upi).matches()) {
            throw new IllegalArgumentException("Enter a UPI ID like name@okbank");
        }
        if (!FieldEncryption.isConfigured()) {
            throw new IllegalStateException("Payouts are not set up yet. Please try again later.");
        }
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("Driver not found"));
        if (driver.getFleet() != Driver.Fleet.INDEPENDENT) {
            throw new IllegalArgumentException("Owner-fleet drivers are paid by the owner directly");
        }
        driver.setPayoutUpiId(upi.toLowerCase(Locale.ROOT));
        driverRepository.save(driver);
        return earnings(driverId);
    }

    /** Every independent driver with their balance - largest amount owed to a driver first. */
    @Transactional(readOnly = true)
    public List<PayoutRow> payoutSummary() {
        Map<Long, BigDecimal> balances = new HashMap<>();
        for (Object[] row : ledger.balances()) {
            balances.put((Long) row[0], (BigDecimal) row[1]);
        }
        return driverRepository.findAll().stream()
                .filter(d -> d.getFleet() == Driver.Fleet.INDEPENDENT)
                .map(d -> new PayoutRow(d.getId(), d.getName(), d.getMobile(), d.getPayoutUpiId(),
                        balances.getOrDefault(d.getId(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing(PayoutRow::balance).reversed())
                .toList();
    }

    /** "ra••••@okaxis": enough for the driver to recognise it, too little to be worth reading. */
    static String mask(String upi) {
        if (upi == null) {
            return null;
        }
        int at = upi.indexOf('@');
        if (at <= 2) {
            return "••" + upi.substring(Math.max(at, 0));
        }
        return upi.substring(0, 2) + "••••" + upi.substring(at);
    }
}

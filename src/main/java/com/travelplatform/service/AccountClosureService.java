package com.travelplatform.service;

import com.travelplatform.entity.BusRoute;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.User;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.DriverLedgerRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.OwnerLedgerRepository;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.UserRepository;
import com.travelplatform.security.TokenVersionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Self-service "Delete account" for travellers, drivers and owners.
 *
 * <p>The row is never removed: bookings, tickets, payments, ledgers and reviews point at it, and
 * that history (GST, payouts, disputes) has to survive. Instead the account is <em>closed</em>:
 * every personal detail is wiped or replaced with a placeholder, the password becomes an unusable
 * random one, every session is revoked, and {@code closed_at} is stamped (V39). Login then fails
 * the same way it does for an unknown account, and the email / mobile is free to register again.
 *
 * <p>Closing is refused while it would strand someone: an open trip or ticket, or money still
 * owed in either direction. The person is told exactly what to finish first.
 */
@Slf4j
@Service
public class AccountClosureService {

    private static final List<TravelBooking.BookingStatus> OPEN_TRIP = List.of(
            TravelBooking.BookingStatus.PENDING, TravelBooking.BookingStatus.CONFIRMED, TravelBooking.BookingStatus.STARTED);
    private static final String CLOSED_NAME = "Closed account";
    private static final SecureRandom RANDOM = new SecureRandom();

    @PersistenceContext
    private EntityManager em;

    private final UserRepository users;
    private final DriverRepository drivers;
    private final OwnerRepository owners;
    private final DriverLedgerRepository driverLedger;
    private final OwnerLedgerRepository ownerLedger;
    private final PasswordEncoder passwordEncoder;
    private final TokenVersionService tokenVersions;

    public AccountClosureService(UserRepository users, DriverRepository drivers, OwnerRepository owners,
                                 DriverLedgerRepository driverLedger, OwnerLedgerRepository ownerLedger,
                                 PasswordEncoder passwordEncoder, TokenVersionService tokenVersions) {
        this.users = users;
        this.drivers = drivers;
        this.owners = owners;
        this.driverLedger = driverLedger;
        this.ownerLedger = ownerLedger;
        this.passwordEncoder = passwordEncoder;
        this.tokenVersions = tokenVersions;
    }

    // ---------------------------------------------------------------- traveller

    @Transactional
    public void closeUser(UUID userId, String password) {
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (user.getClosedAt() != null) {
            return;
        }
        // A Google-only traveller has no password to confirm with; the typed confirmation on the
        // screen is their check. Anyone with a password must give it.
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            requirePassword(password, user.getPassword());
        }

        List<String> blockers = new ArrayList<>();
        long trips = count("SELECT COUNT(b) FROM TravelBooking b WHERE b.userId = :id AND b.status IN :open",
                userId, true);
        if (trips > 0) {
            blockers.add(trips + " car trip(s) not finished yet");
        }
        long tickets = em.createQuery("SELECT COUNT(t) FROM BusTicket t, BusTrip tr WHERE tr.id = t.tripId AND t.userId = :id"
                        + " AND t.status IN (com.travelplatform.entity.BusTicket.Status.PENDING,"
                        + " com.travelplatform.entity.BusTicket.Status.CONFIRMED,"
                        + " com.travelplatform.entity.BusTicket.Status.PARTIALLY_CANCELLED)"
                        + " AND tr.status IN (com.travelplatform.entity.BusTrip.Status.SCHEDULED,"
                        + " com.travelplatform.entity.BusTrip.Status.DEPARTED)", Long.class)
                .setParameter("id", userId).getSingleResult();
        if (tickets > 0) {
            blockers.add(tickets + " bus ticket(s) for a journey still to come");
        }
        long packages = em.createQuery("SELECT COUNT(p) FROM PackageBooking p WHERE p.userId = :id"
                        + " AND p.status IN (com.travelplatform.entity.PackageBooking.BookingStatus.PENDING,"
                        + " com.travelplatform.entity.PackageBooking.BookingStatus.CONFIRMED)", Long.class)
                .setParameter("id", userId).getSingleResult();
        if (packages > 0) {
            blockers.add(packages + " package booking(s) still open");
        }
        refuseIf(blockers);

        em.createQuery("DELETE FROM EmergencyContact c WHERE c.userId = :id").setParameter("id", userId).executeUpdate();

        user.setName(CLOSED_NAME);
        user.setEmail("closed-" + user.getId() + "@closed.invalid");
        user.setPhone(null);
        user.setPassword(null);
        user.setTelegramChatId(null);
        user.setTelegramLinkedAt(null);
        user.setTelegramLinkToken(null);
        user.setTelegramLinkTokenExpiresAt(null);
        user.setClosedAt(LocalDateTime.now());
        tokenVersions.revokeUser(user); // saves
        log.info("Traveller {} closed their account", userId);
    }

    // ---------------------------------------------------------------- driver

    @Transactional
    public void closeDriver(Long driverId, String password) {
        Driver driver = drivers.findById(driverId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (driver.getClosedAt() != null) {
            return;
        }
        requirePassword(password, driver.getPassword());

        List<String> blockers = new ArrayList<>();
        long trips = count("SELECT COUNT(b) FROM TravelBooking b WHERE b.driverId = :id AND b.status IN :open",
                driverId, true);
        if (trips > 0) {
            blockers.add(trips + " trip(s) assigned to you that are not finished");
        }
        BigDecimal balance = driverLedger.balanceOf(driverId);
        if (balance.signum() > 0) {
            blockers.add("₹" + balance.toPlainString() + " in earnings still to be paid out to you");
        } else if (balance.signum() < 0) {
            blockers.add("₹" + balance.negate().toPlainString() + " in dues still to be paid");
        }
        refuseIf(blockers);

        wipeDriver(driver);
        log.info("Driver {} closed their account", driverId);
    }

    // ---------------------------------------------------------------- owner

    /** What closing will also switch off, so the screen can say it before the owner confirms. */
    public record OwnerClosurePreview(int drivers, int vehicles, int busRoutes, int packages) {
    }

    @Transactional(readOnly = true)
    public OwnerClosurePreview previewOwner(Long ownerId) {
        return new OwnerClosurePreview(
                fleetDrivers(ownerId).size(),
                em.createQuery("SELECT COUNT(v) FROM Vehicle v WHERE v.ownerId = :id AND v.active = true", Long.class)
                        .setParameter("id", ownerId).getSingleResult().intValue(),
                em.createQuery("SELECT COUNT(r) FROM BusRoute r WHERE r.ownerId = :id AND r.status = :pub", Long.class)
                        .setParameter("id", ownerId).setParameter("pub", BusRoute.Status.PUBLISHED).getSingleResult().intValue(),
                em.createQuery("SELECT COUNT(p) FROM TravelPackage p WHERE p.ownerId = :id AND p.active = true", Long.class)
                        .setParameter("id", ownerId).getSingleResult().intValue());
    }

    @Transactional
    public void closeOwner(Long ownerId, boolean platform, String password) {
        if (platform) {
            throw new IllegalArgumentException("The NammaJourney platform account cannot be closed");
        }
        Owner owner = owners.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (owner.getClosedAt() != null) {
            return;
        }
        requirePassword(password, owner.getPassword());

        List<String> blockers = new ArrayList<>();
        long trips = count("SELECT COUNT(b) FROM TravelBooking b WHERE b.ownerId = :id AND b.status IN :open",
                ownerId, true);
        if (trips > 0) {
            blockers.add(trips + " car trip(s) not finished yet");
        }
        long tickets = em.createQuery("SELECT COUNT(t) FROM BusTicket t, BusTrip tr WHERE tr.id = t.tripId AND t.ownerId = :id"
                        + " AND t.status IN (com.travelplatform.entity.BusTicket.Status.PENDING,"
                        + " com.travelplatform.entity.BusTicket.Status.CONFIRMED,"
                        + " com.travelplatform.entity.BusTicket.Status.PARTIALLY_CANCELLED)"
                        + " AND tr.status IN (com.travelplatform.entity.BusTrip.Status.SCHEDULED,"
                        + " com.travelplatform.entity.BusTrip.Status.DEPARTED)", Long.class)
                .setParameter("id", ownerId).getSingleResult();
        if (tickets > 0) {
            blockers.add(tickets + " sold bus ticket(s) for journeys still to come");
        }
        long packages = em.createQuery("SELECT COUNT(p) FROM PackageBooking p WHERE p.ownerId = :id"
                        + " AND p.status IN (com.travelplatform.entity.PackageBooking.BookingStatus.PENDING,"
                        + " com.travelplatform.entity.PackageBooking.BookingStatus.CONFIRMED)", Long.class)
                .setParameter("id", ownerId).getSingleResult();
        if (packages > 0) {
            blockers.add(packages + " package booking(s) still open");
        }
        BigDecimal balance = ownerLedger.balanceOf(ownerId);
        if (balance.signum() != 0) {
            blockers.add("₹" + balance.abs().toPlainString() + " still to be settled with NammaJourney");
        }
        List<Driver> fleet = fleetDrivers(ownerId);
        for (Driver d : fleet) {
            BigDecimal db = driverLedger.balanceOf(d.getId());
            if (db.signum() != 0) {
                blockers.add("driver " + d.getName() + " has ₹" + db.abs().toPlainString() + " still to settle");
            }
        }
        refuseIf(blockers);

        // Take everything off sale first, so nothing new can be booked against a closed owner.
        em.createQuery("UPDATE Vehicle v SET v.active = false WHERE v.ownerId = :id")
                .setParameter("id", ownerId).executeUpdate();
        em.createQuery("UPDATE BusRoute r SET r.status = :paused WHERE r.ownerId = :id AND r.status = :pub")
                .setParameter("paused", BusRoute.Status.PAUSED).setParameter("pub", BusRoute.Status.PUBLISHED)
                .setParameter("id", ownerId).executeUpdate();
        em.createQuery("UPDATE TravelPackage p SET p.active = false WHERE p.ownerId = :id")
                .setParameter("id", ownerId).executeUpdate();
        // The owner's own drivers work only for this business; their logins end with it.
        for (Driver d : fleet) {
            wipeDriver(d);
        }

        owner.setContactName(null);
        owner.setEmail("closed-owner-" + owner.getId() + "@closed.invalid");
        owner.setPhone(null);
        owner.setPanBcrypt(null);
        owner.setPanMasked(null);
        owner.setPanHash(null);
        owner.setAadhaarBcrypt(null);
        owner.setAadhaarHash(null);
        owner.setAadhaarLast4(null);
        owner.setRegistrationDocPath(null);
        owner.setPanDocPath(null);
        owner.setAadhaarDocPath(null);
        owner.setBankAccountHolder(null);
        owner.setBankAccountNumber(null);
        owner.setBankIfsc(null);
        owner.setBankName(null);
        owner.setUpiId(null);
        owner.setResubmitTokenHash(null);
        owner.setResubmitTokenExpiresAt(null);
        owner.setPassword(unusablePassword());
        owner.setMustChangePassword(false);
        owner.setClosedAt(LocalDateTime.now());
        tokenVersions.revokeOwner(owner); // saves
        log.info("Owner {} closed their account ({} fleet drivers closed with it)", ownerId, fleet.size());
    }

    // ---------------------------------------------------------------- helpers

    private List<Driver> fleetDrivers(Long ownerId) {
        return drivers.findByOwnerIdAndFleetOrderByCreatedAtDesc(ownerId, Driver.Fleet.OWNER_FLEET).stream()
                .filter(d -> d.getClosedAt() == null)
                .toList();
    }

    private void wipeDriver(Driver driver) {
        long id = driver.getId();
        driver.setName(CLOSED_NAME);
        driver.setMobile("closed-" + id);
        driver.setEmail("closed-driver-" + id + "@closed.invalid");
        driver.setLicenseNumber("CLOSED-" + id);
        driver.setAadhaarHash(null);
        driver.setAadhaarBcrypt(null);
        driver.setAadhaarLast4(null);
        driver.setPhoto(null);
        driver.setLicensePhoto(null);
        driver.setAadhaarPhoto(null);
        driver.setPayoutUpiId(null);
        driver.setTelegramChatId(null);
        driver.setTelegramLinkedAt(null);
        driver.setTelegramLinkToken(null);
        driver.setTelegramLinkTokenExpiresAt(null);
        driver.setVehicleNumber(null);
        driver.setStatus(Driver.Status.INACTIVE);
        driver.setServesCity(false);
        driver.setServesTrip(false);
        driver.setPassword(unusablePassword());
        driver.setClosedAt(LocalDateTime.now());
        tokenVersions.revokeDriver(driver); // saves
    }

    private long count(String jpql, Object id, boolean openTrips) {
        var q = em.createQuery(jpql, Long.class).setParameter("id", id);
        if (openTrips) {
            q.setParameter("open", OPEN_TRIP);
        }
        return q.getSingleResult();
    }

    private void requirePassword(String given, String stored) {
        // 400, not 401: the web app reads 401 as "session expired" and would sign the person out.
        if (given == null || given.isBlank() || !CredentialCheck.matches(passwordEncoder, given, stored)) {
            throw new IllegalArgumentException("Password is wrong");
        }
    }

    private static void refuseIf(List<String> blockers) {
        if (!blockers.isEmpty()) {
            throw new IllegalArgumentException("Your account can't be closed yet: " + String.join("; ", blockers)
                    + ". Finish or cancel these first.");
        }
    }

    private String unusablePassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return passwordEncoder.encode(Base64.getEncoder().encodeToString(bytes));
    }
}

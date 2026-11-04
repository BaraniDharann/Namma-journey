package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusViews.AdminBusView;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.Owner;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.OwnerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The admin reviewing owners' buses (approve or reject) and setting each owner's bus commission. */
@Service
public class BusAdminService {

    static final BigDecimal MAX_COMMISSION = new BigDecimal("50");

    private final BusRepository buses;
    private final OwnerRepository owners;
    private final BusViewMapper mapper;

    public BusAdminService(BusRepository buses, OwnerRepository owners, BusViewMapper mapper) {
        this.buses = buses;
        this.owners = owners;
        this.mapper = mapper;
    }

    /** Buses in a status (oldest submission first), or every bus (newest first) when no status is given. */
    @Transactional(readOnly = true)
    public List<AdminBusView> list(String status) {
        List<Bus> found;
        if (status == null || status.isBlank()) {
            found = buses.findAllByOrderByIdDesc();
        } else {
            Bus.Status s;
            try {
                s = Bus.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Status must be DRAFT, PENDING, APPROVED or REJECTED");
            }
            found = buses.findByStatusOrderBySubmittedAtAscIdAsc(s);
        }
        Map<Long, Owner> ownerById = owners.findAllById(found.stream().map(Bus::getOwnerId).distinct().toList())
                .stream().collect(Collectors.toMap(Owner::getId, Function.identity()));
        return found.stream().map(b -> view(b, ownerById.get(b.getOwnerId()))).toList();
    }

    @Transactional(readOnly = true)
    public AdminBusView detail(Long id) {
        Bus b = bus(id);
        return view(b, owners.findById(b.getOwnerId()).orElse(null));
    }

    @Transactional
    public AdminBusView approve(Long id, Long adminId) {
        Bus b = bus(id);
        if (b.getStatus() != Bus.Status.PENDING) {
            throw new ConflictException("Only a bus waiting for review can be approved");
        }
        b.setStatus(Bus.Status.APPROVED);
        b.setRejectionReason(null);
        b.setReviewedAt(LocalDateTime.now());
        b.setReviewedByAdminId(adminId);
        return detailOf(buses.save(b));
    }

    /** The owner sees the reason, fixes the bus and submits it again. */
    @Transactional
    public AdminBusView reject(Long id, String reason, Long adminId) {
        Bus b = bus(id);
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty() || why.length() > 500) {
            throw new IllegalArgumentException("Give the owner a reason (up to 500 characters)");
        }
        if (b.getStatus() != Bus.Status.PENDING) {
            throw new ConflictException("Only a bus waiting for review can be rejected");
        }
        b.setStatus(Bus.Status.REJECTED);
        b.setRejectionReason(why);
        b.setReviewedAt(LocalDateTime.now());
        b.setReviewedByAdminId(adminId);
        return detailOf(buses.save(b));
    }

    /** The platform's share of the owner's bus fares, 0 to 50%. Applies to tickets booked from now on. */
    @Transactional
    public BigDecimal setCommission(Long ownerId, BigDecimal percent) {
        if (percent == null || percent.signum() < 0 || percent.compareTo(MAX_COMMISSION) > 0) {
            throw new IllegalArgumentException("Commission must be between 0% and 50%");
        }
        Owner o = owners.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        o.setBusCommissionPercent(percent.setScale(2, RoundingMode.HALF_UP));
        return owners.save(o).getBusCommissionPercent();
    }

    private AdminBusView detailOf(Bus b) {
        return view(b, owners.findById(b.getOwnerId()).orElse(null));
    }

    private AdminBusView view(Bus b, Owner o) {
        return AdminBusView.of(mapper.view(b), b.getOwnerId(), o == null ? null : o.getOwnerCode(),
                o == null ? null : o.getTravelsName());
    }

    private Bus bus(Long id) {
        return buses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
    }
}

package com.travelplatform.service.bus;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.dto.bus.BusForms.BusForm;
import com.travelplatform.dto.bus.BusForms.PlaceForm;
import com.travelplatform.dto.bus.BusForms.SeatForm;
import com.travelplatform.dto.bus.BusViews.BusView;
import com.travelplatform.dto.bus.BusViews.PlaceView;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusDocument;
import com.travelplatform.entity.BusPhoto;
import com.travelplatform.entity.BusPlace;
import com.travelplatform.entity.BusSeat;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.BusDocumentRepository;
import com.travelplatform.repository.BusPhotoRepository;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusSeatRepository;
import com.travelplatform.repository.BusTicketRepository;
import com.travelplatform.service.FileStorageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A travels owner's buses (V30): details, seat layout, documents and photos, then submitting the bus
 * for the admin's review. Every call acts for the owner of the current request; another owner's bus
 * is "not found". Also the shared list of towns routes stop at.
 */
@Service
public class BusService {

    /** Indian registration numbers once spaces are gone: TN01AB1234, 22BH1234AA. */
    private static final Pattern PLATE = Pattern.compile("^[A-Z0-9-]{4,20}$");
    static final int MAX_PHOTOS = 10;
    static final int MAX_AMENITIES = 20;

    private final BusRepository buses;
    private final BusSeatRepository seats;
    private final BusDocumentRepository documents;
    private final BusPhotoRepository photos;
    private final BusTicketRepository tickets;
    private final BusPlaceRepository places;
    private final FileStorageService files;
    private final BusViewMapper mapper;
    private final OwnerScope ownerScope = new OwnerScope();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.service.UnscopedLookups lookups;

    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public BusService(BusRepository buses, BusSeatRepository seats, BusDocumentRepository documents,
                      BusPhotoRepository photos, BusTicketRepository tickets, BusPlaceRepository places,
                      FileStorageService files, BusViewMapper mapper) {
        this.buses = buses;
        this.seats = seats;
        this.documents = documents;
        this.photos = photos;
        this.tickets = tickets;
        this.places = places;
        this.files = files;
        this.mapper = mapper;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BusView> list() {
        return buses.findByOwnerIdOrderByIdAsc(ownerScope.ownerId()).stream().map(mapper::view).toList();
    }

    @Transactional(readOnly = true)
    public BusView get(Long id) {
        return mapper.view(mine(id));
    }

    /** A new bus starts as a DRAFT. */
    @Transactional
    public BusView create(BusForm form) {
        Bus b = new Bus();
        b.setOwnerId(ownerScope.ownerId());
        apply(b, form, true);
        return mapper.view(buses.save(b));
    }

    @Transactional
    public BusView update(Long id, BusForm form) {
        Bus b = mine(id);
        apply(b, form, false);
        return mapper.view(buses.save(b));
    }

    /** Replaces the whole layout; refused once any ticket has been sold on the bus. */
    @Transactional
    public BusView replaceSeats(Long id, List<SeatForm> layout) {
        Bus b = mine(id);
        List<BusSeat> fresh = BusLayoutRules.validate(layout);
        if (tickets.countByBusId(id) > 0) {
            throw new ConflictException("Tickets have been sold on this bus, so its seat layout can no longer change");
        }
        seats.deleteByBusId(id);
        for (BusSeat s : fresh) {
            s.setBusId(id);
            s.setOwnerId(b.getOwnerId());
        }
        seats.saveAll(fresh);
        return mapper.view(b);
    }

    /** Uploads (or replaces) one of the four documents, with its expiry date (yyyy-MM-dd). */
    @Transactional
    public BusView uploadDocument(Long id, String type, MultipartFile file, String expiresOn) {
        Bus b = mine(id);
        BusDocument.Type docType;
        try {
            docType = BusDocument.Type.valueOf(String.valueOf(type).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Document type must be RC, PERMIT, INSURANCE or FITNESS");
        }
        LocalDate expiry;
        try {
            expiry = LocalDate.parse(String.valueOf(expiresOn).trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Give the expiry date as yyyy-MM-dd");
        }
        if (expiry.isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("This document has already expired");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a photo or scan of the document");
        }
        String path = files.storeImage(file, "buses/" + id, "doc_" + docType.name().toLowerCase(Locale.ROOT));
        BusDocument doc = documents.findById(new BusDocument.Key(id, docType)).orElse(null);
        String old = null;
        if (doc == null) {
            doc = new BusDocument();
            doc.setBusId(id);
            doc.setType(docType);
            doc.setOwnerId(b.getOwnerId());
        } else {
            old = doc.getPath();
        }
        doc.setPath(path);
        doc.setExpiresOn(expiry);
        doc.setUploadedAt(LocalDateTime.now(clock));
        documents.save(doc);
        if (old != null) {
            files.delete(old);
        }
        return mapper.view(b);
    }

    @Transactional
    public BusView addPhoto(Long id, MultipartFile file) {
        Bus b = mine(id);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a photo");
        }
        List<BusPhoto> existing = photos.findByBusIdOrderByPositionAscIdAsc(id);
        if (existing.size() >= MAX_PHOTOS) {
            throw new IllegalArgumentException("A bus can have at most " + MAX_PHOTOS + " photos");
        }
        BusPhoto p = new BusPhoto();
        p.setBusId(id);
        p.setOwnerId(b.getOwnerId());
        p.setPath(files.storeImage(file, "buses/" + id, "photo"));
        p.setPosition(existing.stream().mapToInt(BusPhoto::getPosition).max().orElse(-1) + 1);
        photos.save(p);
        return mapper.view(b);
    }

    @Transactional
    public BusView deletePhoto(Long id, Long photoId) {
        Bus b = mine(id);
        BusPhoto p = photos.findById(photoId).filter(x -> id.equals(x.getBusId()))
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        photos.delete(p);
        files.delete(p.getPath());
        return mapper.view(b);
    }

    /**
     * Puts the bus in service. The owner was already approved by the admin, so a bus they add needs
     * no second review: it goes live as soon as it has at least one seat, all four documents valid
     * beyond today, and a photo. (A bus left PENDING by the old review flow goes live the same way.)
     */
    @Transactional
    public BusView submit(Long id) {
        Bus b = mine(id);
        if (b.getStatus() == Bus.Status.APPROVED) {
            throw new ConflictException("This bus is already in service");
        }
        if (seats.countByBusId(id) == 0) {
            throw new IllegalArgumentException("Add the seat layout before submitting");
        }
        LocalDate today = LocalDate.now(clock);
        Map<BusDocument.Type, BusDocument> docs = documents.findByBusIdOrderByTypeAsc(id).stream()
                .collect(Collectors.toMap(BusDocument::getType, Function.identity()));
        for (BusDocument.Type t : BusDocument.Type.values()) {
            BusDocument d = docs.get(t);
            if (d == null) {
                throw new IllegalArgumentException("Upload the " + documentName(t) + " before submitting");
            }
            if (!d.getExpiresOn().isAfter(today)) {
                throw new IllegalArgumentException("The " + documentName(t) + " has expired; upload the renewed one");
            }
        }
        if (photos.countByBusId(id) == 0) {
            throw new IllegalArgumentException("Add at least one photo of the bus before submitting");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        b.setStatus(Bus.Status.APPROVED);
        b.setSubmittedAt(now);
        b.setReviewedAt(now);
        b.setRejectionReason(null);
        return mapper.view(buses.save(b));
    }

    /** One of my buses, or "not found". */
    public Bus mine(Long id) {
        Long me = ownerScope.ownerId();
        return buses.findById(id).filter(b -> me.equals(b.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
    }

    // ---------------------------------------------------------------------------------------------
    // Places
    // ---------------------------------------------------------------------------------------------

    /** Autocomplete: up to 10 towns matching the text, names starting with it first. */
    @Transactional(readOnly = true)
    public List<PlaceView> searchPlaces(String q) {
        String text = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<BusPlace> found = text.isEmpty()
                ? places.findAllByOrderByNameAsc(PageRequest.of(0, 10))
                : places.search(text, PageRequest.of(0, 10));
        return found.stream().map(PlaceView::of).toList();
    }

    /** Adds a missing town; a town that already exists (ignoring case) is returned as it is. */
    @Transactional
    public PlaceView addPlace(PlaceForm form) {
        String name = form == null || form.name() == null ? "" : form.name().trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 80) {
            throw new IllegalArgumentException("Give the town's name (2 to 80 characters)");
        }
        BusPlace existing = places.findByNameKey(name).orElse(null);
        if (existing != null) {
            return PlaceView.of(existing);
        }
        String district = form.district() == null ? null : form.district().trim();
        if (district != null && district.length() > 80) {
            throw new IllegalArgumentException("District name is too long");
        }
        BusPlace p = new BusPlace();
        p.setName(name);
        p.setDistrict(district == null || district.isEmpty() ? null : district);
        p.setCreatedByOwnerId(ownerScope.ownerId());
        return PlaceView.of(places.save(p));
    }

    static String documentName(BusDocument.Type t) {
        return switch (t) {
            case RC -> "registration certificate (RC)";
            case PERMIT -> "permit";
            case INSURANCE -> "insurance";
            case FITNESS -> "fitness certificate";
        };
    }

    private void apply(Bus b, BusForm f, boolean creating) {
        if (f == null) {
            throw new IllegalArgumentException("Give the bus's details");
        }
        if (creating || f.registrationNumber() != null) {
            String plate = f.registrationNumber() == null ? ""
                    : f.registrationNumber().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            if (!PLATE.matcher(plate).matches()) {
                throw new IllegalArgumentException("Registration number looks wrong. Use the number plate, e.g. TN 01 AB 1234");
            }
            // Through UnscopedLookups: the row filter hides other owners' buses from this owner.
            (lookups != null ? lookups.busPlateHolder(plate) : buses.findByRegistrationKey(plate).map(Bus::getId))
                    .filter(otherId -> b.getId() == null || !b.getId().equals(otherId))
                    .ifPresent(otherId -> {
                        throw new IllegalArgumentException("Registration number already added");
                    });
            b.setRegistrationNumber(plate);
        }
        if (creating || f.name() != null) {
            String name = f.name() == null ? "" : f.name().trim();
            if (name.isEmpty() || name.length() > 80) {
                throw new IllegalArgumentException("Give the bus a name customers will see (up to 80 characters)");
            }
            b.setName(name);
        }
        if (f.ac() != null) {
            b.setAc(f.ac());
        }
        if (creating || f.permitType() != null) {
            try {
                b.setPermitType(Bus.PermitType.valueOf(String.valueOf(f.permitType()).trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Permit type must be STAGE_CARRIAGE, CONTRACT_CARRIAGE or ALL_INDIA_TOURIST");
            }
        }
        if (creating || f.amenities() != null) {
            b.setAmenities(amenities(f.amenities()));
        }
    }

    private static String amenities(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        Set<String> clean = new LinkedHashSet<>();
        for (String a : list) {
            String t = a == null ? "" : a.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.length() > 40 || t.contains(",")) {
                throw new IllegalArgumentException("Amenity \"" + t + "\" must be under 40 characters, without commas");
            }
            clean.add(t);
        }
        if (clean.size() > MAX_AMENITIES) {
            throw new IllegalArgumentException("List at most " + MAX_AMENITIES + " amenities");
        }
        String joined = String.join(",", new ArrayList<>(clean));
        if (joined.length() > 500) {
            throw new IllegalArgumentException("The amenities list is too long");
        }
        return joined.isEmpty() ? null : joined;
    }
}

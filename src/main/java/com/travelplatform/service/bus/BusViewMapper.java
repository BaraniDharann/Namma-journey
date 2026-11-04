package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusViews.BusView;
import com.travelplatform.dto.bus.BusViews.DocumentView;
import com.travelplatform.dto.bus.BusViews.PhotoView;
import com.travelplatform.dto.bus.BusViews.SeatView;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusSeat;
import com.travelplatform.repository.BusDocumentRepository;
import com.travelplatform.repository.BusPhotoRepository;
import com.travelplatform.repository.BusSeatRepository;
import com.travelplatform.repository.BusTicketRepository;
import com.travelplatform.storage.SignedUploadUrls;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** Builds the bus screens' view of a bus, for its owner and for the admin. Documents get signed links. */
@Component
public class BusViewMapper {

    private final BusDocumentRepository documents;
    private final BusPhotoRepository photos;
    private final BusSeatRepository seats;
    private final BusTicketRepository tickets;
    private final SignedUploadUrls signedUrls;

    public BusViewMapper(BusDocumentRepository documents, BusPhotoRepository photos, BusSeatRepository seats,
                         BusTicketRepository tickets, SignedUploadUrls signedUrls) {
        this.documents = documents;
        this.photos = photos;
        this.seats = seats;
        this.tickets = tickets;
        this.signedUrls = signedUrls;
    }

    public BusView view(Bus b) {
        List<BusSeat> layout = seats.findByBusIdOrderByDeckAscRowNoAscColNoAsc(b.getId());
        return new BusView(b.getId(), b.getRegistrationNumber(), b.getName(), b.isAc(),
                b.getPermitType() == null ? null : b.getPermitType().name(), amenities(b.getAmenities()),
                b.getStatus().name(), b.getRejectionReason(), b.getSubmittedAt(),
                documents.findByBusIdOrderByTypeAsc(b.getId()).stream()
                        .map(d -> new DocumentView(d.getType().name(), signedUrls.signIfSensitive(d.getPath()), d.getExpiresOn()))
                        .toList(),
                photos.findByBusIdOrderByPositionAscIdAsc(b.getId()).stream()
                        .map(p -> new PhotoView(p.getId(), p.getPath(), p.getPosition()))
                        .toList(),
                layout.stream().map(SeatView::of).toList(),
                tickets.countByBusId(b.getId()) > 0,
                BusLayoutRules.typeLabel(b.isAc(), layout));
    }

    static List<String> amenities(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}

package com.travelplatform.service.bus;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.dto.bus.BusForms.BusForm;
import com.travelplatform.dto.bus.BusForms.SeatForm;
import com.travelplatform.dto.bus.BusViews.BusView;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusDocument;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.BusDocumentRepository;
import com.travelplatform.repository.BusPhotoRepository;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusSeatRepository;
import com.travelplatform.repository.BusTicketRepository;
import com.travelplatform.service.FileStorageService;
import com.travelplatform.storage.SignedUploadUrls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** An owner's buses: only their own, submitted only when complete, layout fixed once tickets exist. */
class BusServiceTest {

    private static final long ME = 7L;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    private BusRepository buses;
    private BusSeatRepository seats;
    private BusDocumentRepository documents;
    private BusPhotoRepository photos;
    private BusTicketRepository tickets;
    private FileStorageService files;
    private BusService service;

    @BeforeEach
    void setUp() {
        buses = mock(BusRepository.class);
        seats = mock(BusSeatRepository.class);
        documents = mock(BusDocumentRepository.class);
        photos = mock(BusPhotoRepository.class);
        tickets = mock(BusTicketRepository.class);
        files = mock(FileStorageService.class);
        SignedUploadUrls signer = new SignedUploadUrls("k", "jwt", Duration.ofMinutes(30));
        BusViewMapper mapper = new BusViewMapper(documents, photos, seats, tickets, signer);
        service = new BusService(buses, seats, documents, photos, tickets, mock(BusPlaceRepository.class), files, mapper);
        service.setClock(Clock.fixed(TODAY.atTime(10, 0).atZone(IST).toInstant(), IST));
        when(buses.save(any(Bus.class))).thenAnswer(i -> i.getArgument(0));
        OwnerScope.bind(ME, false);
    }

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    private Bus stored(long id, long owner) {
        Bus b = new Bus();
        b.setId(id);
        b.setOwnerId(owner);
        b.setName("Night Rider");
        b.setRegistrationNumber("TN01AB1234");
        b.setPermitType(Bus.PermitType.STAGE_CARRIAGE);
        when(buses.findById(id)).thenReturn(Optional.of(b));
        return b;
    }

    private void documents(long busId, LocalDate expiry, BusDocument.Type... types) {
        List<BusDocument> list = new ArrayList<>();
        for (BusDocument.Type t : types) {
            BusDocument d = new BusDocument();
            d.setBusId(busId);
            d.setType(t);
            d.setPath("/uploads/buses/" + busId + "/doc_x.jpg");
            d.setExpiresOn(expiry);
            list.add(d);
        }
        when(documents.findByBusIdOrderByTypeAsc(busId)).thenReturn(list);
    }

    @Test
    @DisplayName("a new bus is my DRAFT, with its plate tidied and amenities kept in order")
    void create() {
        BusView v = service.create(new BusForm(" tn 01 ab 1234 ", "Night Rider", false, "stage_carriage",
                List.of("Water bottle", " Blanket ", "Water bottle")));

        assertEquals("TN01AB1234", v.registrationNumber());
        assertEquals("DRAFT", v.status());
        assertEquals(List.of("Water bottle", "Blanket"), v.amenities());
        assertEquals("Non-A/C", v.typeLabel());
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new BusForm("TN01AB1234", "X", true, "TOURIST", List.of())));
    }

    @Test
    @DisplayName("another owner's bus is not found")
    void otherOwnersBus() {
        stored(3L, 99L);
        assertThrows(ResourceNotFoundException.class, () -> service.get(3L));
    }

    @Test
    @DisplayName("submitting needs seats, all four documents valid beyond today, and a photo")
    void submitNeedsEverything() {
        Bus b = stored(3L, ME);
        when(seats.countByBusId(3L)).thenReturn(0L);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.submit(3L)).getMessage().contains("seat"));

        when(seats.countByBusId(3L)).thenReturn(30L);
        documents(3L, TODAY.plusYears(1), BusDocument.Type.RC, BusDocument.Type.PERMIT, BusDocument.Type.INSURANCE);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.submit(3L)).getMessage().contains("fitness"));

        documents(3L, TODAY, BusDocument.Type.values());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.submit(3L)).getMessage().contains("expired"));

        documents(3L, TODAY.plusYears(1), BusDocument.Type.values());
        when(photos.countByBusId(3L)).thenReturn(0L);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.submit(3L)).getMessage().contains("photo"));

        when(photos.countByBusId(3L)).thenReturn(1L);
        b.setStatus(Bus.Status.REJECTED);
        b.setRejectionReason("Blurry RC");
        BusView v = service.submit(3L);
        assertEquals("APPROVED", v.status()); // an approved owner's bus goes live without a second review
        assertNotNull(v.submittedAt());
        assertEquals(null, v.rejectionReason());
        // Document links are signed; the bare path is never handed out.
        assertTrue(v.documents().get(0).path().contains("sig="));
    }

    @Test
    @DisplayName("the seat layout cannot change once a ticket was sold on the bus")
    void layoutLockedByTickets() {
        stored(3L, ME);
        when(tickets.countByBusId(3L)).thenReturn(1L);
        List<SeatForm> layout = List.of(new SeatForm("1", 0, 0, 0, "SEATER", null, null, false));

        assertThrows(ConflictException.class, () -> service.replaceSeats(3L, layout));
        verify(seats, never()).deleteByBusId(any());
    }

    @Test
    @DisplayName("a document upload needs a known type and a date not in the past, and replaces the old file")
    void documentUpload() {
        stored(3L, ME);
        MockMultipartFile file = new MockMultipartFile("file", "rc.jpg", "image/jpeg", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> service.uploadDocument(3L, "LICENCE", file, "2027-01-01"));
        assertThrows(IllegalArgumentException.class, () -> service.uploadDocument(3L, "RC", file, "2026-11-03"));
        assertThrows(IllegalArgumentException.class, () -> service.uploadDocument(3L, "RC", file, "01/01/2027"));

        BusDocument old = new BusDocument();
        old.setBusId(3L);
        old.setType(BusDocument.Type.RC);
        old.setPath("/uploads/buses/3/doc_rc_old.jpg");
        when(documents.findById(new BusDocument.Key(3L, BusDocument.Type.RC))).thenReturn(Optional.of(old));
        when(files.storeImage(eq(file), eq("buses/3"), anyString())).thenReturn("/uploads/buses/3/doc_rc_new.jpg");

        service.uploadDocument(3L, "rc", file, "2027-01-01");

        assertEquals("/uploads/buses/3/doc_rc_new.jpg", old.getPath());
        assertEquals(LocalDate.of(2027, 1, 1), old.getExpiresOn());
        verify(files).delete("/uploads/buses/3/doc_rc_old.jpg");
    }
}

package com.travelplatform.service;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.entity.CarCategory;
import com.travelplatform.entity.Vehicle;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** An owner's cars: plates tidied and unique, only their own cars reachable, none retired mid-trip. */
class VehicleServiceTest {

    private static final long ME = 7L;

    private VehicleRepository vehicles;
    private FileStorageService files;
    private VehicleService service;

    @BeforeEach
    void setUp() {
        vehicles = mock(VehicleRepository.class);
        files = mock(FileStorageService.class);
        when(files.storeFile(any(), anyString())).thenReturn("uploads/drivers/rc_x.jpg");
        when(vehicles.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));
        service = new VehicleService(vehicles, files);
        OwnerScope.bind(ME, false);
    }

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    private static VehicleService.VehicleForm form(String category, String plate, Integer seats) {
        return new VehicleService.VehicleForm(category, plate, "Innova", seats, true);
    }

    private Vehicle stored(long id, long owner) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setOwnerId(owner);
        v.setCategory(CarCategory.SUV);
        v.setRegistrationNumber("KA01AB1234");
        v.setSeats(7);
        when(vehicles.findById(id)).thenReturn(Optional.of(v));
        return v;
    }

    @Test
    @DisplayName("a new car is mine, with its plate in capitals and no spaces")
    void createNormalisesPlate() {
        Vehicle v = service.create(form("suv", " ka 01 ab 1234 ", 7),
                new MockMultipartFile("rc", "rc.jpg", "image/jpeg", new byte[]{1}));

        assertEquals(ME, v.getOwnerId());
        assertEquals("KA01AB1234", v.getRegistrationNumber());
        assertEquals(CarCategory.SUV, v.getCategory());
        assertEquals("uploads/drivers/rc_x.jpg", v.getRcPhoto());
    }

    @Test
    @DisplayName("a plate already added, by anyone, is refused before the RC photo is stored")
    void duplicatePlateRefused() {
        when(vehicles.findByRegistrationKey("KA01AB1234")).thenReturn(Optional.of(new Vehicle()));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.create(form("SEDAN", "KA 01 AB 1234", 4), null));
        assertEquals("Registration number already added", e.getMessage());
        verify(files, never()).storeFile(any(), anyString());
    }

    @Test
    @DisplayName("nonsense plates, unknown categories and impossible seat counts are refused")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> service.create(form("SEDAN", "<x>", 4), null));
        assertThrows(IllegalArgumentException.class, () -> service.create(form("TRUCK", "KA01AB1", 4), null));
        assertThrows(IllegalArgumentException.class, () -> service.create(form("SEDAN", "KA01AB1", 0), null));
        assertThrows(IllegalArgumentException.class, () -> service.create(form("SEDAN", "KA01AB1", 31), null));
        verify(vehicles, never()).save(any());
    }

    @Test
    @DisplayName("another owner's car is not found")
    void otherOwnersVehicleIs404() {
        stored(9, 8L);
        assertThrows(ResourceNotFoundException.class, () -> service.update(9L, form("SUV", null, 6)));
        assertThrows(ResourceNotFoundException.class, () -> service.deactivate(9L));
    }

    @Test
    @DisplayName("a car on an unfinished trip cannot be taken out of use")
    void deactivateRefusedWhileOnTrip() {
        Vehicle v = stored(9, ME);
        when(vehicles.countActiveBookings(9L)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.deactivate(9L));
        assertEquals(true, v.isActive());

        when(vehicles.countActiveBookings(9L)).thenReturn(0L);
        service.deactivate(9L);
        assertFalse(v.isActive());
    }

    @Test
    @DisplayName("my list has only my cars")
    void listIsMine() {
        Vehicle mine = stored(1, ME);
        when(vehicles.findByOwnerIdOrderByIdAsc(ME)).thenReturn(List.of(mine));
        assertEquals(1, service.list().size());
    }
}

package com.travelplatform.service;

import com.travelplatform.dto.VehicleRequest;
import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AdminDriverServiceTest {

    private DriverRepository drivers;
    private FileStorageService files;
    private AdminDriverService service;
    private final MultipartFile img = new MockMultipartFile("f", "a.jpg", "image/jpeg", new byte[]{1});

    @BeforeEach
    void setUp() {
        drivers = mock(DriverRepository.class);
        files = mock(FileStorageService.class);
        when(files.storeFile(any(), anyString())).thenAnswer(i -> "uploads/drivers/" + i.getArgument(1) + "_x.jpg");
        service = new AdminDriverService();
        ReflectionTestUtils.setField(service, "driverRepository", drivers);
        ReflectionTestUtils.setField(service, "fileStorageService", files);
        ReflectionTestUtils.setField(service, "passwordEncoder", new BCryptPasswordEncoder(4));
        ReflectionTestUtils.setField(service, "emailService", mock(EmailService.class));
    }

    private static VehicleRequest car(String plate) {
        return new VehicleRequest("CAR", plate, null, null, null, true, false);
    }

    private Driver holder(long id) {
        Driver d = new Driver();
        d.setId(id);
        return d;
    }

    @Test
    void createRefusesAUsedPlateBeforeStoringFiles() {
        when(drivers.findByVehicleKey("KA01AB1234")).thenReturn(Optional.of(holder(5)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.createDriver(
                "Ravi", "9000000001", "r@x.com", "DL1", "123412341234", img, img, img, car("KA 01 ab 1234")));

        assertEquals("Vehicle number already registered", e.getMessage());
        verify(files, never()).storeFile(any(), anyString());
        verify(drivers, never()).save(any());
    }

    @Test
    void createDeletesStoredFilesWhenSaveFails() {
        when(drivers.save(any(Driver.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> service.createDriver(
                "Ravi", "9000000001", "r@x.com", "DL1", "123412341234", img, img, img, car("KA 01 AB 1234")));

        verify(files).delete("uploads/drivers/photo_x.jpg");
        verify(files).delete("uploads/drivers/license_x.jpg");
        verify(files).delete("uploads/drivers/aadhaar_x.jpg");
    }

    @Test
    void updateRefusesAPlateHeldByAnotherDriver() {
        Driver self = holder(1);
        when(drivers.findById(1L)).thenReturn(Optional.of(self));
        when(drivers.findByVehicleKey("KA01AB1234")).thenReturn(Optional.of(holder(2)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateVehicle(1L, car("KA 01 AB 1234")));

        assertEquals("Vehicle number already registered", e.getMessage());
        verify(drivers, never()).save(any());
    }

    @Test
    void updateAllowsKeepingTheDriversOwnPlate() {
        Driver self = holder(1);
        when(drivers.findById(1L)).thenReturn(Optional.of(self));
        when(drivers.findByVehicleKey("KA01AB1234")).thenReturn(Optional.of(self));
        when(drivers.save(any(Driver.class))).thenAnswer(i -> i.getArgument(0));

        Driver saved = service.updateVehicle(1L, car("KA 01 AB 1234"));

        assertEquals("KA 01 AB 1234", saved.getVehicleNumber());
    }
}

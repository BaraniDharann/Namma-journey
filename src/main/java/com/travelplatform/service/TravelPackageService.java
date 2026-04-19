package com.travelplatform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.dto.TravelPackageRequest;
import com.travelplatform.dto.TravelPackageResponse;
import com.travelplatform.entity.TravelPackage;
import com.travelplatform.repository.PackageBookingRepository;
import com.travelplatform.repository.TravelPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TravelPackageService {

    private final TravelPackageRepository packageRepository;
    private final PackageBookingRepository bookingRepository;
    private final ObjectMapper objectMapper;

    @Cacheable(value = "publicPackages")
    public List<TravelPackageResponse> getAllActivePackages() {
        return packageRepository.findByActiveTrueOrderByCreatedAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Cacheable(value = "packagesByCategory", key = "#category.toLowerCase()")
    public List<TravelPackageResponse> getPackagesByCategory(String category) {
        TravelPackage.PackageCategory cat = TravelPackage.PackageCategory.valueOf(category.toUpperCase());
        return packageRepository.findByCategoryAndActiveTrue(cat)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Cacheable(value = "packagesByState", key = "#state.toLowerCase()")
    public List<TravelPackageResponse> getPackagesByState(String state) {
        return packageRepository.findByStateIgnoreCaseAndActiveTrue(state)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Cacheable(value = "packagesByCatState", key = "#category.toLowerCase() + '::' + #state.toLowerCase()")
    public List<TravelPackageResponse> getPackagesByCategoryAndState(String category, String state) {
        TravelPackage.PackageCategory cat = TravelPackage.PackageCategory.valueOf(category.toUpperCase());
        return packageRepository.findByCategoryAndStateIgnoreCaseAndActiveTrue(cat, state)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Cacheable(value = "publicPackageById", key = "#id")
    public TravelPackageResponse getPackageById(Long id) {
        TravelPackage pkg = packageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found"));
        return toResponse(pkg);
    }

    // Owner methods
    public List<TravelPackageResponse> getAllPackages() {
        return packageRepository.findAll().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "publicPackages", allEntries = true),
            @CacheEvict(value = "packagesByCategory", allEntries = true),
            @CacheEvict(value = "packagesByState", allEntries = true),
            @CacheEvict(value = "packagesByCatState", allEntries = true)
    })
    public TravelPackageResponse createPackage(TravelPackageRequest request, Long ownerId) {
        TravelPackage pkg = new TravelPackage();
        mapRequestToEntity(request, pkg);
        pkg.setCreatedBy(ownerId);
        pkg.setActive(true);
        return toResponse(packageRepository.save(pkg));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "publicPackages", allEntries = true),
            @CacheEvict(value = "publicPackageById", key = "#id"),
            @CacheEvict(value = "packagesByCategory", allEntries = true),
            @CacheEvict(value = "packagesByState", allEntries = true),
            @CacheEvict(value = "packagesByCatState", allEntries = true)
    })
    public TravelPackageResponse updatePackage(Long id, TravelPackageRequest request) {
        TravelPackage pkg = packageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found"));
        mapRequestToEntity(request, pkg);
        return toResponse(packageRepository.save(pkg));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "publicPackages", allEntries = true),
            @CacheEvict(value = "publicPackageById", key = "#id"),
            @CacheEvict(value = "packagesByCategory", allEntries = true),
            @CacheEvict(value = "packagesByState", allEntries = true),
            @CacheEvict(value = "packagesByCatState", allEntries = true)
    })
    public void togglePackageStatus(Long id) {
        TravelPackage pkg = packageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found"));
        pkg.setActive(!pkg.getActive());
        packageRepository.save(pkg);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "publicPackages", allEntries = true),
            @CacheEvict(value = "publicPackageById", key = "#id"),
            @CacheEvict(value = "packagesByCategory", allEntries = true),
            @CacheEvict(value = "packagesByState", allEntries = true),
            @CacheEvict(value = "packagesByCatState", allEntries = true)
    })
    public void deletePackage(Long id) {
        packageRepository.deleteById(id);
    }

    private void mapRequestToEntity(TravelPackageRequest req, TravelPackage pkg) {
        pkg.setName(req.getName());
        pkg.setDescription(req.getDescription());
        pkg.setCategory(TravelPackage.PackageCategory.valueOf(req.getCategory().toUpperCase()));
        pkg.setState(req.getState());
        pkg.setDurationDays(req.getDurationDays());
        pkg.setDurationNights(req.getDurationNights());
        pkg.setPricePerPerson(req.getPricePerPerson());
        pkg.setMaxGroupSize(req.getMaxGroupSize());
        pkg.setFoodIncluded(req.getFoodIncluded() != null ? req.getFoodIncluded() : false);
        pkg.setFoodDetails(req.getFoodDetails());
        pkg.setAccommodationIncluded(req.getAccommodationIncluded() != null ? req.getAccommodationIncluded() : false);
        pkg.setAccommodationDetails(req.getAccommodationDetails());
        pkg.setTollFree(req.getTollFree() != null ? req.getTollFree() : false);
        pkg.setTransportIncluded(req.getTransportIncluded() != null ? req.getTransportIncluded() : false);
        pkg.setTransportDetails(req.getTransportDetails());
        pkg.setGuideIncluded(req.getGuideIncluded() != null ? req.getGuideIncluded() : false);
        pkg.setSightseeingIncluded(req.getSightseeingIncluded() != null ? req.getSightseeingIncluded() : false);
        pkg.setImageUrl(req.getImageUrl());

        try {
            pkg.setPlacesIncluded(objectMapper.writeValueAsString(
                    req.getPlacesIncluded() != null ? req.getPlacesIncluded() : Collections.emptyList()));
            pkg.setHighlights(objectMapper.writeValueAsString(
                    req.getHighlights() != null ? req.getHighlights() : Collections.emptyList()));
            pkg.setItinerary(objectMapper.writeValueAsString(
                    req.getItinerary() != null ? req.getItinerary() : Collections.emptyList()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize package data", e);
        }
    }

    private TravelPackageResponse toResponse(TravelPackage pkg) {
        TravelPackageResponse res = new TravelPackageResponse();
        res.setId(pkg.getId());
        res.setName(pkg.getName());
        res.setDescription(pkg.getDescription());
        res.setCategory(pkg.getCategory().name());
        res.setState(pkg.getState());
        res.setDurationDays(pkg.getDurationDays());
        res.setDurationNights(pkg.getDurationNights());
        res.setPricePerPerson(pkg.getPricePerPerson());
        res.setMaxGroupSize(pkg.getMaxGroupSize());
        res.setFoodIncluded(pkg.getFoodIncluded());
        res.setFoodDetails(pkg.getFoodDetails());
        res.setAccommodationIncluded(pkg.getAccommodationIncluded());
        res.setAccommodationDetails(pkg.getAccommodationDetails());
        res.setTollFree(pkg.getTollFree());
        res.setTransportIncluded(pkg.getTransportIncluded());
        res.setTransportDetails(pkg.getTransportDetails());
        res.setGuideIncluded(pkg.getGuideIncluded());
        res.setSightseeingIncluded(pkg.getSightseeingIncluded());
        res.setImageUrl(pkg.getImageUrl());
        res.setActive(pkg.getActive());
        res.setCreatedAt(pkg.getCreatedAt());
        res.setTotalBookings(bookingRepository.findByPackageIdOrderByBookingDateDesc(pkg.getId()).size());

        try {
            res.setPlacesIncluded(objectMapper.readValue(
                    pkg.getPlacesIncluded(), new TypeReference<List<String>>() {}));
            if (pkg.getHighlights() != null) {
                res.setHighlights(objectMapper.readValue(
                        pkg.getHighlights(), new TypeReference<List<String>>() {}));
            }
            if (pkg.getItinerary() != null) {
                res.setItinerary(objectMapper.readValue(
                        pkg.getItinerary(), new TypeReference<List<TravelPackageRequest.ItineraryDay>>() {}));
            }
        } catch (JsonProcessingException e) {
            res.setPlacesIncluded(Collections.emptyList());
        }

        return res;
    }
}

package com.travelplatform.repository;

import com.travelplatform.entity.TravelPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TravelPackageRepository extends JpaRepository<TravelPackage, Long> {

    List<TravelPackage> findByActiveTrueOrderByCreatedAtDesc();

    List<TravelPackage> findByCategoryAndActiveTrue(TravelPackage.PackageCategory category);

    List<TravelPackage> findByStateIgnoreCaseAndActiveTrue(String state);

    List<TravelPackage> findByCategoryAndStateIgnoreCaseAndActiveTrue(TravelPackage.PackageCategory category, String state);
}

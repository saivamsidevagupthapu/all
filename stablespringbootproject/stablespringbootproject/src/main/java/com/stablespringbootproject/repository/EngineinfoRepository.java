package com.stablespringbootproject.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stablespringbootproject.Entity.EngineInfo;

@Repository
public interface EngineinfoRepository extends JpaRepository<EngineInfo, Long> {

    Optional<EngineInfo> findByVehicleIdAndCountryCode(Long vehicleId, String countryCode);

}
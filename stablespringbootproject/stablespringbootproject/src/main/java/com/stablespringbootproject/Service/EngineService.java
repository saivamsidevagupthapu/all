package com.stablespringbootproject.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.stablespringbootproject.Entity.EngineInfo;
import com.stablespringbootproject.repository.EngineinfoRepository;

@Service
public class EngineService {
	@Autowired
	EngineinfoRepository enginerepo;
	public EngineInfo getEngine(Long id) {
		return enginerepo.findById(id).orElseThrow(()->new RuntimeException("error found"));
	}
	  public EngineInfo getEngineByVehicleAndCountry(Long vehicleId, String countryCode) {
	        return enginerepo.findByVehicleIdAndCountryCode(vehicleId, countryCode)
	                .orElseThrow(() -> new RuntimeException(
	                    "Engine not found for vehicleId: " + vehicleId + 
	                    ", countryCode: " + countryCode));
	    }

}

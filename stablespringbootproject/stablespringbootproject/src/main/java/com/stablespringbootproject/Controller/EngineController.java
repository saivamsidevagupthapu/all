package com.stablespringbootproject.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stablespringbootproject.Entity.EngineInfo;
import com.stablespringbootproject.Service.EngineService;

@RestController
@RequestMapping("/api")
public class EngineController {
	@Autowired
	EngineService engine;
	@GetMapping("/engine/{id}")
	public EngineInfo getEngine(@PathVariable Long id) {
		return engine.getEngine(id);
}
	@GetMapping("/engine/{vehicleId}/{countryCode}")
    public EngineInfo getEngineByVehicleAndCountry(
            @PathVariable Long vehicleId,
            @PathVariable String countryCode) {
        return engine.getEngineByVehicleAndCountry(vehicleId, countryCode);
    }
	
}

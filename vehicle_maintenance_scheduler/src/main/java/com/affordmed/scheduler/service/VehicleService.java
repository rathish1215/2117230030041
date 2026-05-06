package com.affordmed.scheduler.service;

import com.affordmed.scheduler.dto.VehicleDTO;
import com.affordmed.scheduler.dto.VehicleResponseDTO;
import com.affordmed.scheduler.middleware.LoggingMiddleware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Service to fetch vehicle/task data from the external Affordmed evaluation API.
 */
@Service
public class VehicleService {

    private final RestTemplate restTemplate;
    private final LoggingMiddleware.AppLogger logger;

    @Value("${affordmed.api.base-url}")
    private String baseUrl;

    public VehicleService(RestTemplate restTemplate, LoggingMiddleware.AppLogger logger) {
        this.restTemplate = restTemplate;
        this.logger = logger;
    }

    /**
     * Fetches all vehicles/tasks from the external API.
     * GET http://20.207.122.201/evaluation-service/vehicles
     *
     * @return List of VehicleDTO objects
     */
    public List<VehicleDTO> fetchVehicles() {
        String url = baseUrl + "/vehicles";
        logger.log("Fetching vehicles from: " + url);

        try {
            VehicleResponseDTO response = restTemplate.getForObject(url, VehicleResponseDTO.class);
            if (response != null && response.getVehicles() != null) {
                logger.log("Successfully fetched " + response.getVehicles().size() + " vehicles");
                return response.getVehicles();
            }
            logger.log("No vehicles found in response");
            return Collections.emptyList();
        } catch (Exception e) {
            logger.logError("Failed to fetch vehicles from: " + url, e);
            throw new RuntimeException("Failed to fetch vehicles from external API", e);
        }
    }
}

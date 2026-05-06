package com.affordmed.scheduler.service;

import com.affordmed.scheduler.dto.DepotDTO;
import com.affordmed.scheduler.dto.DepotResponseDTO;
import com.affordmed.scheduler.middleware.LoggingMiddleware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Service to fetch depot data from the external Affordmed evaluation API.
 */
@Service
public class DepotService {

    private final RestTemplate restTemplate;
    private final LoggingMiddleware.AppLogger logger;

    @Value("${affordmed.api.base-url}")
    private String baseUrl;

    public DepotService(RestTemplate restTemplate, LoggingMiddleware.AppLogger logger) {
        this.restTemplate = restTemplate;
        this.logger = logger;
    }

    /**
     * Fetches all depots from the external API.
     * GET http://20.207.122.201/evaluation-service/depots
     *
     * @return List of DepotDTO objects
     */
    public List<DepotDTO> fetchDepots() {
        String url = baseUrl + "/depots";
        logger.log("Fetching depots from: " + url);

        try {
            DepotResponseDTO response = restTemplate.getForObject(url, DepotResponseDTO.class);
            if (response != null && response.getDepots() != null) {
                logger.log("Successfully fetched " + response.getDepots().size() + " depots");
                return response.getDepots();
            }
            logger.log("No depots found in response");
            return Collections.emptyList();
        } catch (Exception e) {
            logger.logError("Failed to fetch depots from: " + url, e);
            throw new RuntimeException("Failed to fetch depots from external API", e);
        }
    }
}

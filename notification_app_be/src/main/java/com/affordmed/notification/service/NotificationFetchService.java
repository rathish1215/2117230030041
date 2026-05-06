package com.affordmed.notification.service;

import com.affordmed.notification.dto.NotificationDTO;
import com.affordmed.notification.dto.NotificationResponseDTO;
import com.affordmed.notification.middleware.LoggingMiddleware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Service to fetch notifications from the external Affordmed evaluation API.
 */
@Service
public class NotificationFetchService {

    private final RestTemplate restTemplate;
    private final LoggingMiddleware.AppLogger logger;

    @Value("${affordmed.api.base-url}")
    private String baseUrl;

    public NotificationFetchService(RestTemplate restTemplate, LoggingMiddleware.AppLogger logger) {
        this.restTemplate = restTemplate;
        this.logger = logger;
    }

    /**
     * Fetches all notifications from the external API.
     * GET http://20.207.122.201/evaluation-service/notifications
     */
    public List<NotificationDTO> fetchNotifications() {
        String url = baseUrl + "/notifications";
        logger.log("Fetching notifications from: " + url);

        try {
            NotificationResponseDTO response = restTemplate.getForObject(url, NotificationResponseDTO.class);
            if (response != null && response.getNotifications() != null) {
                logger.log("Successfully fetched " + response.getNotifications().size() + " notifications");
                return response.getNotifications();
            }
            logger.log("No notifications found in response");
            return Collections.emptyList();
        } catch (Exception e) {
            logger.logError("Failed to fetch notifications from: " + url, e);
            throw new RuntimeException("Failed to fetch notifications from external API", e);
        }
    }
}

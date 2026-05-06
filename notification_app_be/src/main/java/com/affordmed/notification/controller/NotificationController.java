package com.affordmed.notification.controller;

import com.affordmed.notification.dto.PriorityNotificationDTO;
import com.affordmed.notification.service.PriorityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the Priority Inbox feature.
 * Fetches notifications from external API, computes priority, returns top N.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final PriorityService priorityService;

    public NotificationController(PriorityService priorityService) {
        this.priorityService = priorityService;
    }

    /**
     * GET /api/notifications/priority?n=10
     *
     * Returns the top N priority notifications.
     * Priority = type_weight (Placement=3 > Result=2 > Event=1) + recency
     *
     * @param n Number of top notifications to return (default: 10)
     */
    @GetMapping("/priority")
    public ResponseEntity<Map<String, Object>> getTopPriorityNotifications(
            @RequestParam(value = "n", defaultValue = "10") int n) {

        List<PriorityNotificationDTO> topNotifications = priorityService.getTopPriorityNotifications(n);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestedCount", n);
        response.put("returnedCount", topNotifications.size());
        response.put("priorityOrder", "Placement (weight=3) > Result (weight=2) > Event (weight=1), then by recency");
        response.put("notifications", topNotifications);

        return ResponseEntity.ok(response);
    }
}

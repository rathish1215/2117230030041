package com.affordmed.notification.service;

import com.affordmed.notification.dto.NotificationDTO;
import com.affordmed.notification.dto.PriorityNotificationDTO;
import com.affordmed.notification.middleware.LoggingMiddleware;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Service that computes priority scores for notifications and returns the top N.
 *
 * Priority is determined by:
 *   1. Type weight: Placement (3) > Result (2) > Event (1)
 *   2. Recency: More recent timestamps get higher scores
 *
 * Uses a Min-Heap (PriorityQueue) of size N for efficient top-N selection.
 * Time Complexity: O(n log N) where n = total notifications, N = top count
 */
@Service
public class PriorityService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Type weights: Placement > Result > Event
    private static final Map<String, Integer> TYPE_WEIGHTS = Map.of(
            "Placement", 3,
            "Result", 2,
            "Event", 1
    );

    private static final long TYPE_WEIGHT_MULTIPLIER = 10_000_000L;

    private final NotificationFetchService fetchService;
    private final LoggingMiddleware.AppLogger logger;

    public PriorityService(NotificationFetchService fetchService, LoggingMiddleware.AppLogger logger) {
        this.fetchService = fetchService;
        this.logger = logger;
    }

    /**
     * Fetches notifications and returns the top N by priority.
     *
     * @param topN Number of top priority notifications to return
     * @return List of PriorityNotificationDTO sorted by priority (highest first)
     */
    public List<PriorityNotificationDTO> getTopPriorityNotifications(int topN) {
        logger.log("Computing top " + topN + " priority notifications...");

        List<NotificationDTO> notifications = fetchService.fetchNotifications();

        if (notifications.isEmpty()) {
            logger.log("No notifications to process");
            return Collections.emptyList();
        }

        // Min-Heap of size topN — keeps the top N highest priority items
        PriorityQueue<PriorityNotificationDTO> minHeap = new PriorityQueue<>(
                Comparator.comparingDouble(PriorityNotificationDTO::getPriorityScore)
        );

        for (NotificationDTO notification : notifications) {
            PriorityNotificationDTO prioritized = computePriority(notification);

            if (minHeap.size() < topN) {
                minHeap.offer(prioritized);
            } else if (prioritized.getPriorityScore() > minHeap.peek().getPriorityScore()) {
                minHeap.poll();  // Remove the lowest priority
                minHeap.offer(prioritized);  // Add the higher priority one
            }
        }

        // Extract from heap and sort descending by priority
        List<PriorityNotificationDTO> result = new ArrayList<>(minHeap);
        result.sort((a, b) -> Double.compare(b.getPriorityScore(), a.getPriorityScore()));

        logger.log("Top " + result.size() + " priority notifications computed successfully");
        for (int i = 0; i < result.size(); i++) {
            PriorityNotificationDTO p = result.get(i);
            logger.log("  #" + (i + 1) + " [" + p.getType() + "] " + p.getMessage()
                    + " (score=" + String.format("%.2f", p.getPriorityScore())
                    + ", weight=" + p.getTypeWeight() + ")");
        }

        return result;
    }

    /**
     * Computes the priority score for a single notification.
     *
     * priority_score = (type_weight × 10,000,000) + recency_score
     *
     * This ensures type ALWAYS dominates. Within same type, recency decides.
     */
    private PriorityNotificationDTO computePriority(NotificationDTO notification) {
        int typeWeight = TYPE_WEIGHTS.getOrDefault(notification.getType(), 0);

        // Parse timestamp and convert to epoch seconds for recency score
        long recencyScore = 0;
        try {
            LocalDateTime dateTime = LocalDateTime.parse(notification.getTimestamp(), TIMESTAMP_FORMAT);
            recencyScore = dateTime.toEpochSecond(ZoneOffset.UTC);
        } catch (Exception e) {
            logger.logError("Failed to parse timestamp: " + notification.getTimestamp(), e);
        }

        double priorityScore = (typeWeight * TYPE_WEIGHT_MULTIPLIER) + recencyScore;

        return new PriorityNotificationDTO(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getTimestamp(),
                typeWeight,
                priorityScore
        );
    }
}

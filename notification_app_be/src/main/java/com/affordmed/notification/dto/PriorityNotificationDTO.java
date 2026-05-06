package com.affordmed.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output DTO — a notification enriched with its computed priority score.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriorityNotificationDTO {

    private String id;
    private String type;
    private String message;
    private String timestamp;
    private int typeWeight;
    private double priorityScore;
}

package com.affordmed.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Wrapper DTO for the Notifications API response.
 * Example: { "notifications": [ ... ] }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDTO {

    @JsonProperty("notifications")
    private List<NotificationDTO> notifications;
}

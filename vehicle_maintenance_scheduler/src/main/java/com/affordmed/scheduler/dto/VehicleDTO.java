package com.affordmed.scheduler.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO mapping a single vehicle/task from the external API.
 * Example: { "TaskID": "264e638f-...", "Duration": 1, "Impact": 5 }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDTO {

    @JsonProperty("TaskID")
    private String taskId;

    @JsonProperty("Duration")
    private int duration;

    @JsonProperty("Impact")
    private int impact;
}

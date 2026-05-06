package com.affordmed.scheduler.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Wrapper DTO for the Vehicles API response.
 * Example: { "vehicles": [ { "TaskID": "...", "Duration": 1, "Impact": 5 }, ... ] }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleResponseDTO {

    @JsonProperty("vehicles")
    private List<VehicleDTO> vehicles;
}

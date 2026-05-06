package com.affordmed.scheduler.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Wrapper DTO for the Depot API response.
 * Example: { "depots": [ { "ID": 1, "MechanicHours": 60 }, ... ] }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepotResponseDTO {

    @JsonProperty("depots")
    private List<DepotDTO> depots;
}

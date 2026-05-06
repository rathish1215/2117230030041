package com.affordmed.scheduler.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO mapping a single depot from the external API.
 * Example: { "ID": 1, "MechanicHours": 60 }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepotDTO {

    @JsonProperty("ID")
    private int id;

    @JsonProperty("MechanicHours")
    private int mechanicHours;
}

package com.affordmed.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Output DTO — represents the optimized schedule for a single depot.
 * Contains the selected vehicles, total duration used, and total impact achieved.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResultDTO {

    private int depotId;
    private int mechanicHoursAvailable;
    private int totalDurationUsed;
    private int totalImpactScore;
    private List<VehicleDTO> selectedVehicles;
}

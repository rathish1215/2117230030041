package com.affordmed.scheduler.service;

import com.affordmed.scheduler.dto.DepotDTO;
import com.affordmed.scheduler.dto.ScheduleResultDTO;
import com.affordmed.scheduler.dto.VehicleDTO;
import com.affordmed.scheduler.middleware.LoggingMiddleware;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Core scheduling service implementing the 0/1 Knapsack Dynamic Programming algorithm.
 *
 * For each depot:
 *   - capacity = depot.MechanicHours
 *   - items = all vehicles (weight = Duration, value = Impact)
 *   - Finds the subset of vehicles that maximizes total Impact
 *     without exceeding the MechanicHours budget.
 *
 * Time Complexity: O(n * W) per depot
 * Space Complexity: O(n * W) per depot
 * where n = number of vehicles, W = mechanic hours capacity
 */
@Service
public class SchedulingService {

    private final DepotService depotService;
    private final VehicleService vehicleService;
    private final LoggingMiddleware.AppLogger logger;

    public SchedulingService(DepotService depotService, VehicleService vehicleService,
                              LoggingMiddleware.AppLogger logger) {
        this.depotService = depotService;
        this.vehicleService = vehicleService;
        this.logger = logger;
    }

    /**
     * Fetches depots and vehicles, then computes optimal schedule for each depot.
     */
    public List<ScheduleResultDTO> computeOptimalSchedules() {
        logger.log("Starting optimal schedule computation...");

        List<DepotDTO> depots = depotService.fetchDepots();
        List<VehicleDTO> vehicles = vehicleService.fetchVehicles();

        logger.log("Computing schedules for " + depots.size() + " depots with " + vehicles.size() + " vehicles");

        List<ScheduleResultDTO> results = new ArrayList<>();

        for (DepotDTO depot : depots) {
            logger.log("Processing Depot ID=" + depot.getId() + " with capacity=" + depot.getMechanicHours() + " hours");
            ScheduleResultDTO result = solveKnapsack(depot, vehicles);
            results.add(result);
            logger.log("Depot ID=" + depot.getId() + " => Selected " + result.getSelectedVehicles().size()
                    + " vehicles, Duration=" + result.getTotalDurationUsed()
                    + "/" + depot.getMechanicHours()
                    + ", Impact=" + result.getTotalImpactScore());
        }

        logger.log("Schedule computation completed for all depots");
        return results;
    }

    /**
     * Solves the 0/1 Knapsack problem for a single depot.
     *
     * @param depot    The depot with its mechanic-hours capacity
     * @param vehicles All available vehicles/tasks
     * @return ScheduleResultDTO with optimal vehicle selection
     */
    private ScheduleResultDTO solveKnapsack(DepotDTO depot, List<VehicleDTO> vehicles) {
        int capacity = depot.getMechanicHours();
        int n = vehicles.size();

        // dp[i][w] = maximum impact using first i items with capacity w
        int[][] dp = new int[n + 1][capacity + 1];

        // Build DP table
        for (int i = 1; i <= n; i++) {
            VehicleDTO vehicle = vehicles.get(i - 1);
            int weight = vehicle.getDuration();
            int value = vehicle.getImpact();

            for (int w = 0; w <= capacity; w++) {
                // Don't take item i
                dp[i][w] = dp[i - 1][w];

                // Take item i (if it fits)
                if (weight <= w) {
                    dp[i][w] = Math.max(dp[i][w], dp[i - 1][w - weight] + value);
                }
            }
        }

        // Backtrack to find which vehicles were selected
        List<VehicleDTO> selectedVehicles = new ArrayList<>();
        int totalDuration = 0;
        int w = capacity;

        for (int i = n; i >= 1; i--) {
            if (dp[i][w] != dp[i - 1][w]) {
                // Item i was included
                VehicleDTO vehicle = vehicles.get(i - 1);
                selectedVehicles.add(vehicle);
                totalDuration += vehicle.getDuration();
                w -= vehicle.getDuration();
            }
        }

        return new ScheduleResultDTO(
                depot.getId(),
                capacity,
                totalDuration,
                dp[n][capacity],
                selectedVehicles
        );
    }
}

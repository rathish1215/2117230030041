package com.affordmed.scheduler.controller;

import com.affordmed.scheduler.dto.ScheduleResponseDTO;
import com.affordmed.scheduler.dto.ScheduleResultDTO;
import com.affordmed.scheduler.service.SchedulingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing the vehicle maintenance scheduling API.
 */
@RestController
@RequestMapping("/evaluation-service")
public class ScheduleController {

    private final SchedulingService schedulingService;

    public ScheduleController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    /**
     * GET /api/schedule
     *
     * Fetches depots and vehicles from external APIs,
     * computes optimal scheduling using 0/1 Knapsack DP,
     * and returns the optimized schedule for each depot.
     */
    @GetMapping("/depots")
    public ResponseEntity<ScheduleResponseDTO> getOptimalSchedule() {
        List<ScheduleResultDTO> schedules = schedulingService.computeOptimalSchedules();
        return ResponseEntity.ok(new ScheduleResponseDTO(schedules));
    }
}

package com.affordmed.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level response DTO wrapping all depot schedules.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponseDTO {

    private List<ScheduleResultDTO> schedules;
}

package com.dispatch.dashboard;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.dispatch.common.error.FieldValidationException;
import com.dispatch.dashboard.dto.DashboardSummary;
import com.dispatch.dashboard.dto.TrendPoint;
import com.dispatch.dashboard.dto.VolumePoint;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return dashboardService.summary();
    }

    @GetMapping("/incident-trend")
    public List<TrendPoint> incidentTrend(@RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) String tz) {
        if (hours < 1 || hours > 168) {
            throw new FieldValidationException("hours", "must be between 1 and 168");
        }
        return dashboardService.incidentTrend(hours, zone(tz));
    }

    @GetMapping("/event-volume")
    public List<VolumePoint> eventVolume(@RequestParam(defaultValue = "60") int minutes) {
        if (minutes < 5 || minutes > 360) {
            throw new FieldValidationException("minutes", "must be between 5 and 360");
        }
        return dashboardService.eventVolume(minutes);
    }

    private static ZoneId zone(String tz) {
        if (tz == null || tz.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(tz);
        } catch (DateTimeException e) {
            throw new FieldValidationException("tz", "is not a valid IANA time zone, e.g. Europe/London");
        }
    }
}

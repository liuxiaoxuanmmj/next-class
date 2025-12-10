package edu.zzttc.backend.service.ics;

import edu.zzttc.backend.domain.entity.Plan;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class IcsService {
    public String exportPlans(List<Plan> plans) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\n");
        sb.append("VERSION:2.0\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        for (Plan p : plans) {
            sb.append("BEGIN:VEVENT\n");
            sb.append("SUMMARY:").append(safe(p.getTitle())).append("\n");
            if (p.getStartTime() != null) sb.append("DTSTART:").append(fmt.format(p.getStartTime())).append("\n");
            if (p.getEndTime() != null) sb.append("DTEND:").append(fmt.format(p.getEndTime())).append("\n");
            if (p.getLocation() != null) sb.append("LOCATION:").append(safe(p.getLocation())).append("\n");
            sb.append("END:VEVENT\n");
        }
        sb.append("END:VCALENDAR\n");
        return sb.toString();
    }

    private String safe(String s) { return s == null ? "" : s.replaceAll("\n", " "); }
}


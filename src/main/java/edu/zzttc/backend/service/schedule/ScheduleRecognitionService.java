package edu.zzttc.backend.service.schedule;

import edu.zzttc.backend.service.schedule.model.ScheduleParsed;

public interface ScheduleRecognitionService {
    ScheduleParsed normalizeAndValidate(ScheduleParsed parsed);
}


package edu.zzttc.backend.service.schedule;

import edu.zzttc.backend.domain.vo.schedule.ScheduleUploadResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface ScheduleImportService {

    /**
     * 从课表截图导入课表数据
     */
    ScheduleUploadResultVO importFromImage(Integer userId,
                                           MultipartFile file,
                                           String termName,
                                           LocalDate startDate,
                                           Integer totalWeeks);

    void clearUserSchedules(Integer userId);
}

package edu.zzttc.backend.service.ai;

import edu.zzttc.backend.domain.vo.schedule.DailyCourseVO;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleQaService {

    /**
     * 根据“日期 + 课表 + 用户问题”生成自然语言回答
     */
    String answer(String question, LocalDate date, List<DailyCourseVO> courses);
}

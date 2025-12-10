package edu.zzttc.backend.service.ai.impl;

import com.alibaba.fastjson2.JSON;
import edu.zzttc.backend.domain.vo.schedule.DailyCourseVO;
import edu.zzttc.backend.service.ai.RagChatService;
import edu.zzttc.backend.service.ai.ScheduleQaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
public class ScheduleQaServiceImpl implements ScheduleQaService {
    @Resource
    private RagChatService ragChatService;

    @Value("${features.ask-ai:true}")
    private boolean askUseAi;

    @Override
    public String answer(String question, LocalDate date, List<DailyCourseVO> courses) {
        if (!askUseAi) {
            if (courses == null || courses.isEmpty()) {
                return date + " 没有安排课程。";
            }
            String basic = JSON.toJSONString(courses);
            return "课程安排：" + basic;
        }
        return ragChatService.answerWithContext(question, date, courses);
    }
}

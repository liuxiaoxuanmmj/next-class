package edu.zzttc.backend.service.ai;

import edu.zzttc.backend.domain.vo.schedule.DailyCourseVO;

import java.time.LocalDate;
import java.util.List;

public interface RagChatService {
    String answerWithContext(String question, LocalDate date, List<DailyCourseVO> courses);
}


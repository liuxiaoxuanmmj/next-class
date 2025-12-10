package edu.zzttc.backend.service.ai.impl;

import edu.zzttc.backend.domain.vo.schedule.DailyCourseVO;
import edu.zzttc.backend.service.ai.RagChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagChatServiceImpl implements RagChatService {
    @Resource
    private ChatClient chatClient;

    @Override
    public String answerWithContext(String question, LocalDate date, List<DailyCourseVO> courses) {
        String dateStr = date != null ? date.format(DateTimeFormatter.ISO_DATE) : "";
        String context = courses == null ? "" : courses.stream()
                .map(c -> String.format("日期=%s; 节次=%d-%d; 课程=%s; 代码=%s; 教师=%s; 教室=%s; 备注=%s",
                        dateStr,
                        c.getSectionStart(),
                        c.getSectionStart() + c.getSectionCount() - 1,
                        nullSafe(c.getCourseName()),
                        nullSafe(c.getCourseCode()),
                        nullSafe(c.getTeacherName()),
                        nullSafe(c.getClassroom()),
                        nullSafe(c.getRemark())))
                .collect(Collectors.joining("\n"));

        String prompt = "你是课程表助手。根据给定的课程事实回答用户问题。若无相关课程，明确告知。";
        return chatClient
                .prompt()
                .system(prompt)
                .user("问题：" + question + "\n事实：\n" + context)
                .call()
                .content();
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}


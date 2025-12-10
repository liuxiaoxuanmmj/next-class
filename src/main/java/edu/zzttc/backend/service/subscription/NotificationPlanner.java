package edu.zzttc.backend.service.subscription;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.zzttc.backend.domain.entity.Account;
import edu.zzttc.backend.domain.entity.SubscriptionPreference;
import edu.zzttc.backend.domain.vo.schedule.DailyCourseVO;
import edu.zzttc.backend.mapper.SubscriptionPreferenceMapper;
import edu.zzttc.backend.service.account.AccountService;
import edu.zzttc.backend.service.schedule.ScheduleQueryService;
import edu.zzttc.backend.service.plan.PlanService;
import edu.zzttc.backend.domain.entity.Plan;
import jakarta.annotation.Resource;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationPlanner {
    @Resource
    private SubscriptionPreferenceMapper prefMapper;
    @Resource
    private ScheduleQueryService scheduleQueryService;
    @Resource
    private AccountService accountService;
    @Resource
    private AmqpTemplate amqpTemplate;
    @Resource
    private PlanService planService;

    @Scheduled(cron = "0 * * * * *")
    public void planDailyDigest() {
        List<SubscriptionPreference> prefs = prefMapper.selectList(Wrappers.<SubscriptionPreference>lambdaQuery().eq(SubscriptionPreference::getSubscribed, true));
        for (SubscriptionPreference p : prefs) {
            if (!shouldSendNow(p)) continue;
            Account acc = accountService.getById(p.getUserId());
            if (acc == null || acc.getEmail() == null) continue;
            java.time.ZoneId zoneId = java.time.ZoneId.of(safeZone(p.getTimezone()));
            LocalDate todayLocal = LocalDate.now(zoneId);
            List<DailyCourseVO> courses = scheduleQueryService.queryByDate(p.getUserId(), todayLocal);
            java.time.LocalDateTime start = java.time.LocalDateTime.of(todayLocal, java.time.LocalTime.MIN);
            java.time.LocalDateTime end = java.time.LocalDateTime.of(todayLocal, java.time.LocalTime.MAX);
            java.util.List<Plan> plans = planService.listByRange(p.getUserId(), start, end);
            Map<String, Object> data = new HashMap<>();
            data.put("type", "digest");
            data.put("email", acc.getEmail());
            // 纯文本内容（回退，课程+计划）
            data.put("content", buildDigestAll(todayLocal, courses, plans));
            // HTML 模板渲染所需数据
            data.put("useHtml", true);
            data.put("date", String.valueOf(todayLocal));
            data.put("itemsHtml", buildDigestHtml(courses));
            data.put("plansHtml", buildPlansHtml(plans));
            amqpTemplate.convertAndSend("email", data);
            // 更新去重标记
            p.setLastSentDate(todayLocal);
            prefMapper.updateById(p);
        }
    }

    private String buildDigest(LocalDate date, List<DailyCourseVO> courses) {
        StringBuilder sb = new StringBuilder();
        sb.append("日期：").append(date).append("\n");
        if (courses == null || courses.isEmpty()) {
            sb.append("今日无课程。");
            return sb.toString();
        }
        for (DailyCourseVO c : courses) {
            sb.append("第").append(c.getSectionStart()).append("-")
                    .append(c.getSectionStart() + c.getSectionCount() - 1)
                    .append("节：")
                    .append(nullSafe(c.getCourseName()))
                    .append(" ")
                    .append(nullSafe(c.getClassroom()))
                    .append(" ")
                    .append(nullSafe(c.getTeacherName()))
                    .append("\n");
        }
        return sb.toString();
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    /**
     * 构建摘要的 HTML 表格片段
     */
    private String buildDigestHtml(List<DailyCourseVO> courses) {
        if (courses == null || courses.isEmpty()) {
            return "<p>今日无课程。</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<table>");
        sb.append("<thead><tr><th>节次</th><th>课程</th><th>教室</th><th>教师</th></tr></thead><tbody>");
        for (DailyCourseVO c : courses) {
            int end = c.getSectionStart() + c.getSectionCount() - 1;
            sb.append("<tr>");
            sb.append("<td>").append(c.getSectionStart()).append("-").append(end).append("节</td>");
            sb.append("<td>").append(escape(nullSafe(c.getCourseName()))).append("</td>");
            sb.append("<td>").append(escape(nullSafe(c.getClassroom()))).append("</td>");
            sb.append("<td>").append(escape(nullSafe(c.getTeacherName()))).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * 计划 HTML 片段
     */
    private String buildPlansHtml(List<Plan> plans) {
        if (plans == null || plans.isEmpty()) return "<p>今日无计划。</p>";
        StringBuilder sb = new StringBuilder();
        sb.append("<table>");
        sb.append("<thead><tr><th>时间</th><th>标题</th><th>地点</th><th>优先级</th></tr></thead><tbody>");
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        for (Plan p : plans) {
            String timeRange = "";
            if (p.getStartTime() != null) {
                timeRange = fmt.format(p.getStartTime());
            }
            if (p.getEndTime() != null) {
                timeRange = timeRange + "-" + fmt.format(p.getEndTime());
            }
            sb.append("<tr>")
              .append("<td>").append(escape(timeRange)).append("</td>")
              .append("<td>").append(escape(nullSafe(p.getTitle()))).append("</td>")
              .append("<td>").append(escape(nullSafe(p.getLocation()))).append("</td>")
              .append("<td>").append(escape(nullSafe(p.getPriority()))).append("</td>")
              .append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    /**
     * 课程+计划的纯文本摘要
     */
    private String buildDigestAll(LocalDate date, List<DailyCourseVO> courses, List<Plan> plans) {
        StringBuilder sb = new StringBuilder();
        sb.append("日期：").append(date).append("\n");
        sb.append("【课程】\n").append(buildDigest(date, courses)).append("\n");
        sb.append("【计划】\n");
        if (plans == null || plans.isEmpty()) {
            sb.append("今日无计划。\n");
        } else {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
            for (Plan p : plans) {
                String timeRange = "";
                if (p.getStartTime() != null) timeRange = fmt.format(p.getStartTime());
                if (p.getEndTime() != null) timeRange = timeRange + "-" + fmt.format(p.getEndTime());
                sb.append(timeRange).append(" ")
                        .append(nullSafe(p.getTitle())).append(" ")
                        .append(nullSafe(p.getLocation())).append(" ")
                        .append(nullSafe(p.getPriority())).append("\n");
            }
        }
        return sb.toString();
    }

    private boolean shouldSendNow(SubscriptionPreference p) {
        String tz = safeZone(p.getTimezone());
        java.time.ZoneId zone = java.time.ZoneId.of(tz);
        java.time.LocalDateTime now = java.time.LocalDateTime.now(zone);
        java.time.LocalDate today = now.toLocalDate();
        // 去重：当天已发则忽略
        if (p.getLastSentDate() != null && today.equals(p.getLastSentDate())) return false;
        // 时间匹配：HH:mm 等于 dailyTime
        String hhmm = String.format("%02d:%02d", now.getHour(), now.getMinute());
        String wanted = safeDaily(p.getDailyTime());
        return hhmm.equals(wanted);
    }

    private String safeZone(String z) {
        try {
            if (z == null || z.isBlank()) return "Asia/Shanghai";
            java.time.ZoneId.of(z); // 校验
            return z;
        } catch (Exception e) {
            return "Asia/Shanghai";
        }
    }

    private String safeDaily(String t) {
        if (t == null || t.isBlank()) return "07:00";
        // 允许 "HH:mm" 或 "HH:mm:ss"，统一到 HH:mm
        if (t.length() >= 5) return t.substring(0,5);
        return "07:00";
    }
}

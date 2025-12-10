package edu.zzttc.backend.controller;

import edu.zzttc.backend.domain.dto.ScheduleAskDTO;
import edu.zzttc.backend.domain.entity.Account;
import edu.zzttc.backend.domain.entity.RestBean;
import edu.zzttc.backend.domain.vo.schedule.DailyCourseVO;
import edu.zzttc.backend.domain.vo.schedule.ScheduleUploadResultVO;
import edu.zzttc.backend.service.account.AccountService;
import edu.zzttc.backend.service.ai.ScheduleQaService;
import edu.zzttc.backend.service.schedule.ScheduleImportService;
import edu.zzttc.backend.service.schedule.ScheduleQueryService;
import edu.zzttc.backend.utils.ScheduleFilterUtils;
import edu.zzttc.backend.utils.ScheduleTimeResolver;
import edu.zzttc.backend.mapper.ScheduleImportMapper;
import edu.zzttc.backend.domain.entity.ScheduleImport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import java.util.concurrent.CompletableFuture;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "课表查询接口")
@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    @Resource
    private ScheduleQueryService scheduleQueryService;

    @Resource
    private AccountService accountService;

    @Resource
    private ScheduleImportService scheduleImportService;

    @Resource
    private ScheduleQaService scheduleQaService;

    @Resource
    private ScheduleImportMapper scheduleImportMapper;

    @Resource
    private edu.zzttc.backend.mapper.ScheduleItemMapper scheduleItemMapper;

    @jakarta.annotation.Resource
    private edu.zzttc.backend.utils.JwtUtils utils;

    /**
     * 今日课表（使用当前登录用户 + 今日日期）
     */
    @Operation(summary = "查询今日课表")
    @GetMapping("/today")
    public RestBean<List<DailyCourseVO>> today() {
        Integer userId = currentUserId();
        List<DailyCourseVO> list = scheduleQueryService.queryByDate(userId, LocalDate.now());
        return RestBean.success(list);
    }

    /**
     * 指定日期课表，例如 /api/schedule/date?date=2025-09-01
     */
    @Operation(summary = "按日期查询课表")
    @GetMapping("/date")
    public RestBean<List<DailyCourseVO>> byDate(@RequestParam String date) {
        Integer userId = currentUserId();
        LocalDate d = LocalDate.parse(date); // 前端传 yyyy-MM-dd
        List<DailyCourseVO> list = scheduleQueryService.queryByDate(userId, d);
        return RestBean.success(list);
    }

    /**
     * 按周次查询整周课表
     * 说明：
     * - 若未传 week，则依据 date（若传）或“今天”计算周次；
     * - 学期选择以 date（若传）或“今天”为参考日期，取最近一个 startDate <= 参考日期 的学期；
     * - 返回该周的所有课程列表。
     * 示例：
     * - /api/schedule/week （自动以今天计算周次）
     * - /api/schedule/week?date=2025-09-01
     * - /api/schedule/week?week=7
     * - /api/schedule/week?date=2025-09-01&week=7
     */
    @Operation(summary = "按周次查询整周课表")
    @GetMapping("/week")
    public RestBean<List<DailyCourseVO>> byWeek(@RequestParam(value = "week", required = false) Integer week,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Integer userId = currentUserId();
        List<DailyCourseVO> list = scheduleQueryService.queryByWeek(userId, date, week);
        return RestBean.success(list);
    }

    /**
     * 查询当前用户是否已导入课表
     * 逻辑：在 db_schedule_import 中查找 user_id=当前用户 且 status='SUCCESS' 的记录是否存在
     * 返回：true=已导入；false=未导入
     */
    @Operation(summary = "查询是否已导入课表")
    @GetMapping("/import/status")
    public RestBean<Boolean> importStatus() {
        Integer userId = currentUserId();
        long count = scheduleImportMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ScheduleImport>lambdaQuery()
                        .eq(ScheduleImport::getUserId, userId)
                        .eq(ScheduleImport::getStatus, "SUCCESS"));
        return RestBean.success(count > 0);
    }

    /**
     * 上传课表截图并导入（需要登录）
     */
    @Operation(summary = "上传课表截图并导入", description = "上传一张课表截图，生成课程和排课数据")
    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RestBean<ScheduleUploadResultVO> uploadImage(@RequestPart("file") MultipartFile file,
            @RequestParam("termName") String termName,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "totalWeeks", required = false) Integer totalWeeks) {
        Integer userId = currentUserId();
        long count = scheduleImportMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ScheduleImport>lambdaQuery()
                        .eq(ScheduleImport::getUserId, userId)
                        .eq(ScheduleImport::getStatus, "SUCCESS"));
        if (count > 0) {
            scheduleImportService.clearUserSchedules(userId);
        }
        ScheduleUploadResultVO vo = scheduleImportService.importFromImage(userId, file, termName, startDate,
                totalWeeks);
        return RestBean.success(vo);
    }

    @Operation(summary = "清空当前用户的课表数据")
    @PostMapping("/clear")
    public RestBean<Void> clear() {
        Integer userId = currentUserId();
        scheduleImportService.clearUserSchedules(userId);
        return RestBean.success();
    }

    @Operation(summary = "人工修正排课节次")
    @PostMapping("/items/{id}/sections")
    public RestBean<Void> updateSections(@PathVariable Integer id,
            @RequestParam Integer sectionStart,
            @RequestParam Integer sectionCount) {
        Integer userId = currentUserId();
        if (id == null || sectionStart == null || sectionCount == null || sectionStart < 1 || sectionCount < 1) {
            return RestBean.failure(400, "参数不合法");
        }
        edu.zzttc.backend.domain.entity.ScheduleItem item = scheduleItemMapper.selectById(id);
        if (item == null) {
            return RestBean.failure(404, "记录不存在");
        }
        if (!java.util.Objects.equals(item.getUserId(), userId)) {
            return RestBean.failure(403, "无权修改他人记录");
        }
        item.setSectionStart(sectionStart);
        item.setSectionCount(sectionCount);
        String classroomFull = item.getClassroom();
        String weekPart = (item.getWeekStart() != null && item.getWeekEnd() != null)
                ? (java.util.Objects.equals(item.getWeekStart(), item.getWeekEnd())
                        ? item.getWeekStart() + "周"
                        : item.getWeekStart() + "-" + item.getWeekEnd() + "周")
                : "周次未知";
        int endSec = sectionStart + sectionCount - 1;
        String sectionPart = sectionCount == 1 ? ("第" + sectionStart + "节") : ("第" + sectionStart + "-" + endSec + "节");
        item.setRawTimeExpr(
                weekPart + "," + sectionPart + "," + java.util.Optional.ofNullable(classroomFull).orElse("教室未知"));
        scheduleItemMapper.updateById(item);
        return RestBean.success();
    }

    @PostMapping("/ask")
    @PreAuthorize("isAuthenticated()")
    public Object ask(@RequestBody ScheduleAskDTO dto,
            @RequestParam(value = "stream", required = false) Boolean stream,
            HttpServletRequest request) {
        Integer userId = currentUserId();
        String question = dto.getQuestion();

        // 1. 基准日期：如果传了 date，就当做“参考今天”；否则就是真正的今天
        LocalDate baseDate = dto.getDate() != null ? dto.getDate() : LocalDate.now();

        // 2. 解析“今天 / 明天 / 这周五 / 下周一 / 下午 / 晚上”等模糊时间
        ScheduleTimeResolver.ResolvedTime rt = ScheduleTimeResolver.resolve(question, baseDate);
        LocalDate targetDate = rt.getDate();

        List<DailyCourseVO> allCourses = scheduleQueryService.queryByDate(userId, targetDate);
        List<DailyCourseVO> filtered = ScheduleFilterUtils.filterByPeriod(allCourses, rt.getPeriod());

        String formatted = formatCourses(targetDate, rt.getPeriod(), filtered);

        java.util.Map<String, Object> context = new java.util.LinkedHashMap<>();
        context.put("date", java.util.Objects.toString(targetDate, ""));
        context.put("period", rt.getPeriod());
        context.put("items", filtered);

        boolean wantStream = Boolean.TRUE.equals(stream) ||
                (request.getHeader("Accept") != null && request.getHeader("Accept").contains("text/event-stream"));
        if (wantStream) {
            String authorization = request.getHeader("Authorization");
            SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
            Runnable task = () -> {
                try {
                    emitter.send(SseEmitter.event().name("context").data(context));
                    String answer = scheduleQaService.answer(question, targetDate, filtered);
                    int chunk = 100;
                    for (int i = 0; i < answer.length(); i += chunk) {
                        int end = Math.min(i + chunk, answer.length());
                        String part = answer.substring(i, end);
                        emitter.send(SseEmitter.event().name("delta").data(part));
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {
                        }
                        if ((i / chunk) % 20 == 0) {
                            String authHeader = authorization;
                            com.auth0.jwt.interfaces.DecodedJWT jwt = utils.resolveJwt(authHeader);
                            if (jwt == null) {
                                java.util.Map<String, Object> authErr = new java.util.LinkedHashMap<>();
                                authErr.put("error", "认证失效，请重新登录");
                                emitter.send(SseEmitter.event().name("auth_error").data(authErr));
                                emitter.complete();
                                return;
                            }
                        }
                    }
                    java.util.Map<String, Object> done = new java.util.LinkedHashMap<>();
                    done.put("answer", answer);
                    emitter.send(SseEmitter.event().name("done").data(done));
                    emitter.complete();
                } catch (Exception e) {
                    // 不抛回到过滤链，避免响应已提交后再次写入导致错误
                    try {
                        java.util.Map<String, Object> done = new java.util.LinkedHashMap<>();
                        done.put("answer", "服务异常，稍后重试");
                        done.put("error", e.getMessage());
                        emitter.send(SseEmitter.event().name("done").data(done));
                    } catch (Exception ignored) {
                    }
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                    }
                }
            };
            DelegatingSecurityContextRunnable wrapped = new DelegatingSecurityContextRunnable(task,
                    org.springframework.security.core.context.SecurityContextHolder.getContext());
            CompletableFuture.runAsync(wrapped);
            return emitter;
        }
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("answer", scheduleQaService.answer(question, targetDate, filtered));
        payload.put("context", context);
        return RestBean.success(payload);
    }

    private String formatCourses(LocalDate date,
            edu.zzttc.backend.utils.ScheduleTimeResolver.DayPeriod period,
            List<DailyCourseVO> courses) {
        String periodText = switch (period) {
            case MORNING -> "上午";
            case AFTERNOON -> "下午";
            case EVENING -> "晚上";
            default -> "";
        };
        if (courses == null || courses.isEmpty()) {
            String prefix = (periodText.isEmpty() ? "今天" : ("今天" + periodText));
            return prefix + "没有安排课程。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你今天").append(periodText.isEmpty() ? "" : periodText).append("有").append(courses.size())
                .append("节课：\n\n");
        for (int i = 0; i < courses.size(); i++) {
            DailyCourseVO c = courses.get(i);
            int start = c.getSectionStart() == null ? 0 : c.getSectionStart();
            int end = start + (c.getSectionCount() == null ? 0 : c.getSectionCount()) - 1;
            if (end < start)
                end = start;
            sb.append(i + 1).append(". **第").append(start).append("-").append(end).append("节**  \n")
                    .append("   - 课程：").append(safe(c.getCourseName())).append("  \n")
                    .append("   - 教师：").append(safe(c.getTeacherName())).append("  \n")
                    .append("   - 教室：").append(safe(c.getClassroom())).append("  \n\n");
        }
        sb.append("请按时上课，注意教室位置！");
        return sb.toString();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private Integer currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Account account = accountService.findAccountByUsernameOrEmail(username);
        return account.getId();
    }
}

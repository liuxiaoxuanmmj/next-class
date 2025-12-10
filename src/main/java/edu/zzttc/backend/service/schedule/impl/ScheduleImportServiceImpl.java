package edu.zzttc.backend.service.schedule.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.alibaba.fastjson2.JSON;
import edu.zzttc.backend.domain.entity.Course;
import edu.zzttc.backend.domain.entity.ScheduleImport;
import edu.zzttc.backend.domain.entity.ScheduleItem;
import edu.zzttc.backend.domain.entity.TermConfig;
import edu.zzttc.backend.domain.vo.schedule.ScheduleUploadResultVO;
import edu.zzttc.backend.mapper.CourseMapper;
import edu.zzttc.backend.mapper.ScheduleImportMapper;
import edu.zzttc.backend.mapper.ScheduleItemMapper;
import edu.zzttc.backend.mapper.TermConfigMapper;
import edu.zzttc.backend.service.ai.ScheduleAiService;
import edu.zzttc.backend.service.schedule.ScheduleImportService;
import edu.zzttc.backend.service.schedule.model.ScheduleParsed;
import edu.zzttc.backend.service.schedule.model.ScheduleParsedCourse;
import edu.zzttc.backend.service.schedule.model.ScheduleParsedItem;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 课表截图导入实现：
 * 1. 保存图片到本地
 * 2. 根据学期信息找到/创建 TermConfig
 * 3. 调用大模型解析课表截图（失败则回退到假数据）
 * 4. 写入 db_course / db_schedule_item / db_schedule_import
 */
@Slf4j
@Service
public class ScheduleImportServiceImpl implements ScheduleImportService {

    @Resource
    private TermConfigMapper termConfigMapper;
    @Resource
    private CourseMapper courseMapper;
    @Resource
    private ScheduleItemMapper scheduleItemMapper;
    @Resource
    private ScheduleImportMapper scheduleImportMapper;
    @Resource
    private ScheduleAiService scheduleAiService;
    @Resource
    private edu.zzttc.backend.service.schedule.ScheduleRecognitionService scheduleRecognitionService;

    /**
     * 上传图片的基础目录，可以在 yml 中配置：
     * schedule:
     * upload-dir: F:/base-code/uploads/schedule
     */
    @Value("${schedule.upload-dir:F:/schedule-uploads}")
    private String uploadDir;

    private final ConcurrentHashMap<Integer, ReentrantLock> importLocks = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public ScheduleUploadResultVO importFromImage(Integer userId,
            MultipartFile file,
            String termName,
            LocalDate startDate,
            Integer totalWeeks) {
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("课表图片不能为空");
        }
        if (termName == null || startDate == null) {
            throw new IllegalArgumentException("学期名称和开始日期不能为空");
        }

        ReentrantLock lock = importLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        boolean locked = false;
        try {
            if (!lock.tryLock(30, TimeUnit.SECONDS)) {
                throw new RuntimeException("当前账号正在导入课表，请稍后再试");
            }
            locked = true;
            // 1. 保存图片到本地
            String imageUrl = saveImage(file);

            // 2. 找到或创建学期配置
            TermConfig term = ensureTermConfig(userId, termName, startDate, totalWeeks);

            // 3. 调用大模型解析课表截图
            ScheduleParsed parsed;
            try {
                parsed = scheduleAiService.parseScheduleFromImage(imageUrl, term);
            } catch (Exception e) {
                log.error("课表 AI 解析失败，imageUrl={}", imageUrl, e);

                // 3.1 写一条“失败”的导入记录，方便排查
                ScheduleParsed empty = new ScheduleParsed();
                empty.setImageUrl(imageUrl);
                empty.setRawOcrText("AI_ERROR: " + e.getMessage());
                empty.setCourses(Collections.emptyList());
                empty.setItems(Collections.emptyList());
                saveImportRecord(userId, term.getId(), imageUrl, empty, "FAIL_AI");

                // 3.2 抛出业务异常，让前端收到“导入失败”的友好提示
                throw new RuntimeException("课表识别失败，请检查截图是否清晰、完整后重新上传");
            }

            parsed = scheduleRecognitionService.normalizeAndValidate(parsed);
            Long has = scheduleImportMapper.selectCount(
                    Wrappers.<ScheduleImport>lambdaQuery()
                            .eq(ScheduleImport::getUserId, userId)
                            .eq(ScheduleImport::getStatus, "SUCCESS"));
            if (has != null && has > 0) {
                clearUserSchedules(userId);
            }
            int courseCount = upsertCoursesAndItems(userId, term, parsed);

            // 5. 写入“成功”的导入记录
            saveImportRecord(userId, term.getId(), imageUrl, parsed, "SUCCESS");

            // 6. 返回结果
            ScheduleUploadResultVO vo = new ScheduleUploadResultVO();
            vo.setTermId(term.getId());
            vo.setCourseCount(courseCount);
            vo.setItemCount(parsed.getItems().size());
            vo.setImageUrl(imageUrl);
            return vo;
        } catch (IOException e) {
            log.error("保存课表截图失败", e);
            throw new RuntimeException("保存课表截图失败，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("导入任务被中断，请稍后重试");
        } finally {
            if (locked) {
                lock.unlock();
            }
            ReentrantLock l = importLocks.get(userId);
            if (l != null && !l.isLocked() && !l.hasQueuedThreads()) {
                importLocks.remove(userId, l);
            }
        }
    }

    /**
     * 保存图片到本地目录，并返回一个“路径字符串”作为 imageUrl
     */
    private String saveImage(MultipartFile file) throws IOException {
        Files.createDirectories(Paths.get(uploadDir));
        String originalName = Optional.ofNullable(file.getOriginalFilename())
                .orElse("timetable.png");
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) {
            ext = originalName.substring(dot);
        }
        String filename = "schedule_" + System.currentTimeMillis() + ext;
        Path target = Paths.get(uploadDir, filename);
        file.transferTo(target.toFile());
        log.info("课表截图已保存到: {}", target.toAbsolutePath());
        // 这里简单返回绝对路径，如果以后接 OSS，可以改成公网 URL
        return target.toAbsolutePath().toString();
    }

    /**
     * 保证存在一条对应学期配置：同 user + term_name 唯一
     */
    private TermConfig ensureTermConfig(Integer userId,
            String termName,
            LocalDate startDate,
            Integer totalWeeks) {
        TermConfig term = termConfigMapper.selectOne(
                Wrappers.<TermConfig>lambdaQuery()
                        .eq(TermConfig::getUserId, userId)
                        .eq(TermConfig::getTermName, termName));
        if (term != null) {
            // 如果已存在，可以选择是否更新 start_date / total_weeks，这里简单覆盖
            term.setStartDate(startDate);
            term.setTotalWeeks(totalWeeks);
            termConfigMapper.updateById(term);
            return term;
        }

        term = new TermConfig();
        term.setUserId(userId);
        term.setTermName(termName);
        term.setStartDate(startDate);
        term.setTotalWeeks(totalWeeks);
        termConfigMapper.insert(term);
        return term;
    }

    /**
     * 根据解析结果拼出类似：
     * 9-13周,第1、2节,第二教学楼104
     */
    private String buildRawTimeExpr(ScheduleParsedItem pi) {
        // 周次部分
        String weekPart;
        if (pi.getWeekStart() != null && pi.getWeekEnd() != null) {
            if (Objects.equals(pi.getWeekStart(), pi.getWeekEnd())) {
                weekPart = pi.getWeekStart() + "周";
            } else {
                weekPart = pi.getWeekStart() + "-" + pi.getWeekEnd() + "周";
            }
        } else {
            weekPart = "周次未知";
        }

        // 节次部分（注意：我们会在前面先把 sectionCount 纠正成 2）
        String sectionPart;
        Integer start = pi.getSectionStart();
        Integer count = pi.getSectionCount();
        if (start != null && count != null) {
            int endSec = start + count - 1;
            if (count == 1) {
                // 单节（极少情况），就保持单节
                sectionPart = "第" + start + "节";
            } else if (count == 2) {
                // 电子科大学校课表：一个方块通常连上两节
                sectionPart = "第" + start + "、" + (start + 1) + "节";
            } else {
                // 兼容将来可能出现的 3、4 节连续课
                sectionPart = "第" + start + "-" + endSec + "节";
            }
        } else {
            sectionPart = "节次未知";
        }

        // 教室
        String classroom = Optional.ofNullable(pi.getClassroom()).orElse("教室未知");

        return weekPart + "," + sectionPart + "," + classroom;
    }

    /**
     * 电子科大课表：一个彩色方块通常占用两节课。
     * 如果大模型解析出的 sectionCount 为空或为 1，我们在导入时统一纠正为 2，
     * 保证：
     * - DB 中 section_count 为 2
     * - 文本展示为 “第1、2节”
     */
    private void normalizeSectionCount(ScheduleParsedItem pi) {
        Integer c = pi.getSectionCount();
        if (c == null || c <= 0) {
            pi.setSectionCount(1);
        }
    }

    @Override
    @Transactional
    public void clearUserSchedules(Integer userId) {
        if (userId == null)
            return;
        scheduleItemMapper.delete(
                Wrappers.<ScheduleItem>lambdaQuery()
                        .eq(ScheduleItem::getUserId, userId));
        courseMapper.delete(
                Wrappers.<Course>lambdaQuery()
                        .eq(Course::getUserId, userId));
        scheduleImportMapper.delete(
                Wrappers.<ScheduleImport>lambdaQuery()
                        .eq(ScheduleImport::getUserId, userId)
                        .eq(ScheduleImport::getStatus, "SUCCESS"));
    }

    /**
     * 落库：课程 upsert + 排课 insert
     */
    private int upsertCoursesAndItems(Integer userId, TermConfig term, ScheduleParsed parsed) {
        Map<String, Integer> courseIdMap = new HashMap<>();
        int newCourseCount = 0;

        // 1. 处理课程
        for (ScheduleParsedCourse pc : parsed.getCourses()) {
            Course course = courseMapper.selectOne(
                    Wrappers.<Course>lambdaQuery()
                            .eq(Course::getUserId, userId)
                            .eq(Course::getTermId, term.getId())
                            .eq(Course::getCourseName, pc.getCourseName())
                            .eq(Course::getTeacherName, pc.getTeacherName()));
            if (course == null) {
                course = new Course();
                course.setUserId(userId);
                course.setTermId(term.getId());
                course.setCourseName(pc.getCourseName());
                course.setCourseCode(pc.getCourseCode());
                course.setTeacherName(java.util.Optional.ofNullable(pc.getTeacherName()).orElse(""));
                course.setCredit(pc.getCredit());
                course.setColorTag(pc.getColorTag());
                courseMapper.insert(course);
                newCourseCount++;
            } else {
                course.setCourseCode(pc.getCourseCode());
                course.setTeacherName(java.util.Optional.ofNullable(pc.getTeacherName()).orElse(""));
                course.setCredit(pc.getCredit());
                course.setColorTag(pc.getColorTag());
                courseMapper.updateById(course);
            }
            String key = pc.getCourseName() + "@@" + java.util.Optional.ofNullable(pc.getTeacherName()).orElse("");
            courseIdMap.put(key, course.getId());
        }

        // 2. 处理排课明细（支持幂等：当前已做全量清理后再插入）
        for (ScheduleParsedItem pi : parsed.getItems()) {

            normalizeSectionCount(pi);

            String ckey = pi.getCourseName() + "@@" + java.util.Optional.ofNullable(pi.getTeacherName()).orElse("");
            Integer courseId = courseIdMap.get(ckey);
            if (courseId == null) {
                log.warn("找不到课程 [{}] 的ID，跳过该条排课", pi.getCourseName());
                continue;
            }

            ScheduleItem item = new ScheduleItem();
            item.setUserId(userId);
            item.setTermId(term.getId());
            item.setCourseId(courseId);
            item.setDayOfWeek(pi.getDayOfWeek());
            item.setSectionStart(pi.getSectionStart());
            item.setSectionCount(pi.getSectionCount());
            item.setWeekStart(pi.getWeekStart());
            item.setWeekEnd(pi.getWeekEnd());
            item.setWeekOddEven(pi.getWeekOddEven());
            item.setClassroom(pi.getClassroom());
            item.setCampus(pi.getCampus());
            item.setRemark(pi.getRemark());
            item.setRawTimeExpr(buildRawTimeExpr(pi));
            scheduleItemMapper.insert(item);
        }

        return newCourseCount;
    }

    /**
     * 保存导入记录
     */
    private void saveImportRecord(Integer userId,
            Integer termId,
            String imageUrl,
            ScheduleParsed parsed,
            String status) {
        ScheduleImport imp = new ScheduleImport();
        imp.setUserId(userId);
        imp.setTermId(termId);
        imp.setImageUrl(imageUrl);
        imp.setRawOcrText(parsed.getRawOcrText());
        imp.setParsedJson(JSON.toJSONString(parsed));
        imp.setStatus(status); // SUCCESS / FAIL_AI
        imp.setCreatedAt(new Date());
        scheduleImportMapper.insert(imp);
    }

}

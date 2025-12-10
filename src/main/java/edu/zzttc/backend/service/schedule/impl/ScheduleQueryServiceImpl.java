package edu.zzttc.backend.service.schedule.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.zzttc.backend.domain.entity.Course;
import edu.zzttc.backend.domain.entity.ScheduleItem;
import edu.zzttc.backend.domain.entity.TermConfig;
import edu.zzttc.backend.domain.vo.schedule.DailyCourseVO;
import edu.zzttc.backend.mapper.CourseMapper;
import edu.zzttc.backend.mapper.ScheduleItemMapper;
import edu.zzttc.backend.mapper.TermConfigMapper;
import edu.zzttc.backend.service.schedule.ScheduleQueryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleQueryServiceImpl implements ScheduleQueryService {

    @Resource
    private TermConfigMapper termConfigMapper;
    @Resource
    private ScheduleItemMapper scheduleItemMapper;
    @Resource
    private CourseMapper courseMapper;

    @Override
    public List<DailyCourseVO> queryByDate(Integer userId, LocalDate date) {
        if (userId == null || date == null) {
            return Collections.emptyList();
        }

        // 1. 找到“当前日期所在的学期”
        TermConfig term = termConfigMapper.selectOne(
                Wrappers.<TermConfig>lambdaQuery()
                        .eq(TermConfig::getUserId, userId)
                        .le(TermConfig::getStartDate, date)
                        .orderByDesc(TermConfig::getStartDate)
                        .last("LIMIT 1")
        );
        if (term == null) {
            return Collections.emptyList();
        }

        long days = ChronoUnit.DAYS.between(term.getStartDate(), date);
        if (days < 0) {
            return Collections.emptyList();
        }
        int week = (int) (days / 7) + 1;

        // 如果配置了总周数，超过则认为不在学期内
        if (term.getTotalWeeks() != null && week > term.getTotalWeeks()) {
            return Collections.emptyList();
        }

        int dayOfWeek = date.getDayOfWeek().getValue(); // 1=周一,...,7=周日

        // 2. 查询这一天的排课记录
        List<ScheduleItem> rawItems = scheduleItemMapper.selectList(
                Wrappers.<ScheduleItem>lambdaQuery()
                        .eq(ScheduleItem::getUserId, userId)
                        .eq(ScheduleItem::getTermId, term.getId())
                        .eq(ScheduleItem::getDayOfWeek, dayOfWeek)
                        .le(ScheduleItem::getWeekStart, week) // week_start <= week
                        .ge(ScheduleItem::getWeekEnd, week)   // week_end >= week
                        .and(w -> w.eq(ScheduleItem::getWeekOddEven, 0)
                                .or()
                                .eq(ScheduleItem::getWeekOddEven, (week % 2 == 1) ? 1 : 2))
        );

        if (rawItems.isEmpty()) {
            return Collections.emptyList();
        }

        // 2.1 去重：同一时段优先保留与当前周奇偶匹配的记录
        Map<String, ScheduleItem> pick = new LinkedHashMap<>();
        boolean isOdd = (week % 2 == 1);
        for (ScheduleItem it : rawItems) {
            String key = it.getSectionStart() + ":" + it.getSectionCount() + ":" + it.getCourseId() + ":" +
                    Optional.ofNullable(it.getClassroom()).orElse("");
            ScheduleItem old = pick.get(key);
            if (old == null) {
                pick.put(key, it);
            } else {
                int oldOE = Optional.ofNullable(old.getWeekOddEven()).orElse(0);
                int newOE = Optional.ofNullable(it.getWeekOddEven()).orElse(0);
                boolean oldMatch = (oldOE == 1 && isOdd) || (oldOE == 2 && !isOdd);
                boolean newMatch = (newOE == 1 && isOdd) || (newOE == 2 && !isOdd);
                if (newMatch && !oldMatch) {
                    pick.put(key, it);
                } else if (oldOE == 0 && newOE != 0) {
                    pick.put(key, it);
                }
            }
        }
        List<ScheduleItem> items = new ArrayList<>(pick.values());

        // 3. 批量查询课程信息
        Set<Integer> courseIds = items.stream()
                .map(ScheduleItem::getCourseId)
                .collect(Collectors.toSet());

        List<Course> courses = courseMapper.selectBatchIds(courseIds);
        Map<Integer, Course> courseMap = courses.stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        // 4. 组装 VO
        List<DailyCourseVO> result = new ArrayList<>();
        for (ScheduleItem item : items) {
            Course c = courseMap.get(item.getCourseId());
            if (c == null) {
                continue;
            }
            DailyCourseVO vo = new DailyCourseVO();
            vo.setCourseName(c.getCourseName());
            vo.setCourseCode(c.getCourseCode());
            vo.setTeacherName(c.getTeacherName());
            vo.setDayOfWeek(dayOfWeek);
            vo.setWeek(week);
            vo.setSectionStart(item.getSectionStart());
            vo.setSectionCount(item.getSectionCount());
            vo.setClassroom(item.getClassroom());
            vo.setRemark(item.getRemark());
            result.add(vo);
        }

        // 按节次排序
        result.sort(Comparator.comparing(DailyCourseVO::getSectionStart));
        return result;
    }

    /**
     * 按周次查询整周课表
     * 说明：
     *  - 基于 refDate 选择学期；若 week 为空则依据 refDate 计算周次
     *  - 过滤规则：week_start <= week <= week_end，且满足 week_odd_even（0=全部；1=单周；2=双周）
     *  - 返回按 dayOfWeek、sectionStart 排序后的整周课程列表
     */
    @Override
    public List<DailyCourseVO> queryByWeek(Integer userId, LocalDate refDate, Integer week) {
        if (userId == null) {
            return Collections.emptyList();
        }

        LocalDate baseDate = (refDate != null) ? refDate : LocalDate.now();

        // 1. 基于参考日期选择学期（取最近一个 startDate <= baseDate 的学期）
        TermConfig term = termConfigMapper.selectOne(
                Wrappers.<TermConfig>lambdaQuery()
                        .eq(TermConfig::getUserId, userId)
                        .le(TermConfig::getStartDate, baseDate)
                        .orderByDesc(TermConfig::getStartDate)
                        .last("LIMIT 1")
        );
        if (term == null) {
            return Collections.emptyList();
        }

        // 2. 计算或校验目标周次
        int targetWeek;
        if (week == null) {
            long days = ChronoUnit.DAYS.between(term.getStartDate(), baseDate);
            if (days < 0) {
                return Collections.emptyList();
            }
            targetWeek = (int) (days / 7) + 1;
        } else {
            targetWeek = week;
        }

        if (targetWeek <= 0) {
            return Collections.emptyList();
        }
        if (term.getTotalWeeks() != null && targetWeek > term.getTotalWeeks()) {
            return Collections.emptyList();
        }

        // 3. 读取整周的排课记录（按周次范围筛选；单双周在内存中过滤）
        List<ScheduleItem> items = scheduleItemMapper.selectList(
                Wrappers.<ScheduleItem>lambdaQuery()
                        .eq(ScheduleItem::getUserId, userId)
                        .eq(ScheduleItem::getTermId, term.getId())
                        .le(ScheduleItem::getWeekStart, targetWeek) // week_start <= targetWeek
                        .ge(ScheduleItem::getWeekEnd, targetWeek)   // week_end >= targetWeek
        );

        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        // 3.1 单/双周过滤
        List<ScheduleItem> filtered = new ArrayList<>();
        boolean isOddWeek = (targetWeek % 2 == 1);
        for (ScheduleItem it : items) {
            int oe = Optional.ofNullable(it.getWeekOddEven()).orElse(0);
            if (oe == 0 || (oe == 1 && isOddWeek) || (oe == 2 && !isOddWeek)) {
                filtered.add(it);
            }
        }
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. 批量查询课程信息
        Set<Integer> courseIds = filtered.stream()
                .map(ScheduleItem::getCourseId)
                .collect(Collectors.toSet());
        List<Course> courses = courseMapper.selectBatchIds(courseIds);
        Map<Integer, Course> courseMap = courses.stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        // 5. 组装 VO
        List<DailyCourseVO> result = new ArrayList<>();
        for (ScheduleItem item : filtered) {
            Course c = courseMap.get(item.getCourseId());
            if (c == null) continue;
            DailyCourseVO vo = new DailyCourseVO();
            vo.setCourseName(c.getCourseName());
            vo.setCourseCode(c.getCourseCode());
            vo.setTeacherName(c.getTeacherName());
            vo.setDayOfWeek(item.getDayOfWeek());
            vo.setWeek(targetWeek);
            vo.setSectionStart(item.getSectionStart());
            vo.setSectionCount(item.getSectionCount());
            vo.setClassroom(item.getClassroom());
            vo.setRemark(item.getRemark());
            result.add(vo);
        }

        // 6. 排序：星期升序，其次开始节次升序
        result.sort(Comparator
                .comparing(DailyCourseVO::getDayOfWeek)
                .thenComparing(DailyCourseVO::getSectionStart));
        return result;
    }
}

package edu.zzttc.backend.service.schedule.impl;

import edu.zzttc.backend.service.schedule.ScheduleRecognitionService;
import edu.zzttc.backend.service.schedule.model.ScheduleParsed;
import edu.zzttc.backend.service.schedule.model.ScheduleParsedItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ScheduleRecognitionServiceImpl implements ScheduleRecognitionService {
    @Override
    public ScheduleParsed normalizeAndValidate(ScheduleParsed parsed) {
        if (parsed == null)
            return null;
        List<ScheduleParsedItem> items = parsed.getItems();
        if (items == null) {
            parsed.setItems(new ArrayList<>());
            return parsed;
        }
        List<ScheduleParsedItem> result = new ArrayList<>();
        for (ScheduleParsedItem it : items) {
            if (it == null)
                continue;
            if (it.getDayOfWeek() == null || it.getSectionStart() == null || it.getSectionCount() == null)
                continue;
            if (it.getSectionCount() <= 0)
                it.setSectionCount(1);
            // 周次缺失兜底
            if (it.getWeekStart() == null && it.getWeekEnd() != null)
                it.setWeekStart(it.getWeekEnd());
            if (it.getWeekEnd() == null && it.getWeekStart() != null)
                it.setWeekEnd(it.getWeekStart());
            if (it.getWeekStart() == null && it.getWeekEnd() == null) {
                it.setWeekStart(1);
                it.setWeekEnd(1);
            }
            if (it.getWeekStart() != null && it.getWeekEnd() != null && it.getWeekStart() > it.getWeekEnd()) {
                Integer tmp = it.getWeekStart();
                it.setWeekStart(it.getWeekEnd());
                it.setWeekEnd(tmp);
            }
            if (Objects.equals(it.getWeekOddEven(), 1) || Objects.equals(it.getWeekOddEven(), 2)) {
                // 对齐起止周的奇偶性
                if (Objects.equals(it.getWeekOddEven(), 1)) { // 单周
                    if ((it.getWeekStart() % 2) == 0)
                        it.setWeekStart(it.getWeekStart() + 1);
                    if ((it.getWeekEnd() % 2) == 0)
                        it.setWeekEnd(it.getWeekEnd() - 1);
                } else { // 双周
                    if ((it.getWeekStart() % 2) == 1)
                        it.setWeekStart(it.getWeekStart() + 1);
                    if ((it.getWeekEnd() % 2) == 1)
                        it.setWeekEnd(it.getWeekEnd() - 1);
                }
                if (it.getWeekStart() > it.getWeekEnd()) {
                    // 修正后若出现 start>end，则回退为单点周次
                    it.setWeekEnd(it.getWeekStart());
                }
            }
            result.add(it);
        }
        // 额外规范化（修订版）：仅在“完全相同的周次 + 老师 + 教室 + 课程名 + 星期”下，合并节次为并集；
        // 禁止跨周次合并，避免不同周次被错误并为一个更长的节次区间。
        List<ScheduleParsedItem> merged = new ArrayList<>();
        java.util.Map<String, List<ScheduleParsedItem>> groups = new java.util.LinkedHashMap<>();
        for (ScheduleParsedItem it : result) {
            String key = (it.getDayOfWeek() + "|" + safe(it.getCourseName()) + "|" + safe(it.getClassroom())
                    + "|" + safe(it.getTeacherName()) + "|" + it.getWeekStart() + "-" + it.getWeekEnd() + "-"
                    + java.util.Objects.toString(it.getWeekOddEven(), "0"));
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
        }
        for (java.util.Map.Entry<String, List<ScheduleParsedItem>> e : groups.entrySet()) {
            List<ScheduleParsedItem> g = e.getValue();
            if (g.size() <= 1) {
                merged.addAll(g);
                continue;
            }
            java.util.Set<Integer> secSet = new java.util.TreeSet<>();
            for (ScheduleParsedItem it : g) {
                int start = it.getSectionStart();
                int end = start + it.getSectionCount() - 1;
                for (int s = start; s <= end; s++)
                    secSet.add(s);
            }
            int minSec = secSet.iterator().next();
            int maxSec = minSec;
            for (Integer s : secSet) {
                if (s > maxSec)
                    maxSec = s;
            }
            boolean contiguous = (secSet.size() == (maxSec - minSec + 1));
            if (!contiguous) {
                // 若并集不连续，则不合并，保留原条目
                merged.addAll(g);
                continue;
            }
            ScheduleParsedItem base = g.get(0);
            ScheduleParsedItem copy = new ScheduleParsedItem();
            copy.setCourseName(base.getCourseName());
            copy.setTeacherName(base.getTeacherName());
            copy.setDayOfWeek(base.getDayOfWeek());
            copy.setSectionStart(minSec);
            copy.setSectionCount(maxSec - minSec + 1);
            copy.setWeekStart(base.getWeekStart());
            copy.setWeekEnd(base.getWeekEnd());
            copy.setWeekOddEven(base.getWeekOddEven());
            copy.setClassroom(base.getClassroom());
            copy.setCampus(base.getCampus());
            copy.setRemark(base.getRemark());
            copy.setRawTimeExpr(base.getRawTimeExpr());
            merged.add(copy);
        }
        parsed.setItems(merged);
        return parsed;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}

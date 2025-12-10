package edu.zzttc.backend.utils;

import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class ScheduleTimeResolver {

    /** 一天中的时间段 */
    public enum DayPeriod {
        ALL,        // 不区分时段
        MORNING,    // 上午
        AFTERNOON,  // 下午
        EVENING     // 晚上
    }

    /** 解析结果：具体日期 + 时间段 */
    @Data
    public static class ResolvedTime {
        private LocalDate date;
        private DayPeriod period = DayPeriod.ALL;
    }

    private ScheduleTimeResolver() {}

    /**
     * 解析 “前天/昨天/今天/明天/后天/这周五/下周一/下午/晚上” 等模糊时间
     * @param question 用户问题，如“我这周五下午有什么课？”
     * @param baseDate 参考日期，一般是今天（或传入 dto 的 date）
     */
    public static ResolvedTime resolve(String question, LocalDate baseDate) {
        ResolvedTime rt = new ResolvedTime();
        if (baseDate == null) {
            baseDate = LocalDate.now();
        }
        rt.setDate(baseDate);

        if (question == null || question.isBlank()) {
            return rt;
        }

        String q = question.replaceAll("\\s+", "");

        // ========= 1. 相对日期：前天 / 昨天 / 今天 / 明天 / 后天 =========
        // 注意顺序：先匹配更“远”的，避免比如“前天”里也包含“天”之类的误伤
        if (q.contains("前天")) {
            rt.setDate(baseDate.minusDays(2));
        } else if (q.contains("昨天")) {
            rt.setDate(baseDate.minusDays(1));
        } else if (q.contains("后天")) {
            rt.setDate(baseDate.plusDays(2));
        } else if (q.contains("明天") || q.contains("翌日")) {
            rt.setDate(baseDate.plusDays(1));
        } else if (q.contains("今天") || q.contains("今日") || q.contains("当日")) {
            rt.setDate(baseDate);
        }

        // ========= 2. 周几：本周 / 下周 / 上周 + 周一~周日 =========
        int weekOffset = 0; // 0=本周, 1=下周, 2=下下周, -1=上周
        if (q.contains("下下周")) {
            weekOffset = 2;
        } else if (q.contains("下周") || q.contains("下星期") || q.contains("下礼拜")) {
            weekOffset = 1;
        } else if (q.contains("上周") || q.contains("上星期") || q.contains("上礼拜")) {
            weekOffset = -1;
        } else if (q.contains("这周") || q.contains("本周") || q.contains("这星期") || q.contains("本星期")) {
            weekOffset = 0;
        }

        Integer targetDow = null;
        if (q.matches(".*(周一|星期一|礼拜一).*")) targetDow = 1;
        else if (q.matches(".*(周二|星期二|礼拜二).*")) targetDow = 2;
        else if (q.matches(".*(周三|星期三|礼拜三).*")) targetDow = 3;
        else if (q.matches(".*(周四|星期四|礼拜四).*")) targetDow = 4;
        else if (q.matches(".*(周五|星期五|礼拜五).*")) targetDow = 5;
        else if (q.matches(".*(周六|星期六|礼拜六).*")) targetDow = 6;
        else if (q.matches(".*(周日|星期日|星期天|礼拜天).*")) targetDow = 7;

        if (targetDow != null) {
            // 注意这里是基于 baseDate 所在周的周一来偏移 weekOffset
            // 如果上面因为“昨天/前天/明天/后天”改过 date，这里就是以“解析完的那天所在周”为基准
            LocalDate current = rt.getDate();
            DayOfWeek baseDow = current.getDayOfWeek(); // 1-7
            LocalDate monday = current.minusDays(baseDow.getValue() - 1L);
            LocalDate targetDate = monday.plusWeeks(weekOffset).plusDays(targetDow - 1L);
            rt.setDate(targetDate);
        }

        // ========= 3. 时间段：上午 / 下午 / 晚上 =========
        if (q.contains("上午") || q.contains("早上") || q.contains("早晨") || q.contains("早上课")) {
            rt.setPeriod(DayPeriod.MORNING);
        } else if (q.contains("下午") || q.contains("中午")) {
            rt.setPeriod(DayPeriod.AFTERNOON);
        } else if (q.contains("晚上") || q.contains("晚间") || q.contains("夜里") || q.contains("夜晚")) {
            rt.setPeriod(DayPeriod.EVENING);
        }

        return rt;
    }
}

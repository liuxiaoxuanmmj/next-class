package edu.zzttc.backend.utils;

import edu.zzttc.backend.domain.vo.schedule.DailyCourseVO;
import edu.zzttc.backend.utils.ScheduleTimeResolver.DayPeriod;

import java.util.List;
import java.util.stream.Collectors;

public final class ScheduleFilterUtils {

    private ScheduleFilterUtils() {}

    /**
     * 按“上午 / 下午 / 晚上”过滤课表
     * 这里假设：
     *   1-4 节 = 上午
     *   5-8 节 = 下午
     *   9-12节 = 晚上
     * 如果你学校节次不一样，可以自己改区间。
     */
    public static List<DailyCourseVO> filterByPeriod(List<DailyCourseVO> list, DayPeriod period) {
        if (list == null || list.isEmpty() || period == DayPeriod.ALL) {
            return list;
        }

        int start;
        int end;
        switch (period) {
            case MORNING -> {
                start = 1; end = 4;
            }
            case AFTERNOON -> {
                start = 5; end = 8;
            }
            case EVENING -> {
                start = 9; end = 12;
            }
            default -> {
                return list;
            }
        }

        int finalStart = start;
        int finalEnd = end;

        return list.stream()
                .filter(c -> {
                    int s = c.getSectionStart();
                    int e = s + c.getSectionCount() - 1;
                    // 只要有交集就算在该时段
                    return e >= finalStart && s <= finalEnd;
                })
                .collect(Collectors.toList());
    }
}

package edu.zzttc.backend.service.schedule;

import edu.zzttc.backend.domain.vo.schedule.DailyCourseVO;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleQueryService {

    /**
     * 按日期查询某个用户这一天的课程
     */
    List<DailyCourseVO> queryByDate(Integer userId, LocalDate date);

    /**
     * 按周次查询某个用户整周的课程
     * @param userId 当前登录用户ID
     * @param refDate 学期参考日期（用于选择对应学期；为空则默认今天）
     * @param week 目标周次（为空则依据 refDate 计算周次）
     * @return 该周所有课程（包含星期、节次、课程、教室等）
     */
    List<DailyCourseVO> queryByWeek(Integer userId, LocalDate refDate, Integer week);
}

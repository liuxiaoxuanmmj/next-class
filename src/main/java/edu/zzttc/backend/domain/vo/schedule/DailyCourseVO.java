package edu.zzttc.backend.domain.vo.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "单条排课展示信息")
public class DailyCourseVO {

    @Schema(description = "课程名称")
    private String courseName;

    @Schema(description = "课程代码")
    private String courseCode;

    @Schema(description = "任课老师")
    private String teacherName;

    @Schema(description = "星期几：1=周一,...,7=周日")
    private Integer dayOfWeek;

    @Schema(description = "第几周")
    private Integer week;

    @Schema(description = "开始节次")
    private Integer sectionStart;

    @Schema(description = "连续节数")
    private Integer sectionCount;

    @Schema(description = "教室")
    private String classroom;

    @Schema(description = "备注/说明")
    private String remark;
}

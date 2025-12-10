package edu.zzttc.backend.service.schedule.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 大模型解析出的“排课明细”
 * 与 db_schedule_item 表结构一一对应
 */
@Data
@Schema(description = "课表解析后的单条排课记录")
public class ScheduleParsedItem implements Serializable {

    @Schema(description = "课程名称（需与 courses 中的 courseName 对应）", example = "网络安全攻防技术")
    private String courseName;

    private String teacherName;

    @Schema(description = "星期几：1=周一 … 7=周日", example = "1")
    private Integer dayOfWeek;

    @Schema(description = "开始节次（大节编号，从 1 开始）", example = "1")
    private Integer sectionStart;

    @Schema(description = "连续节数，比如 2 表示第1-2节", example = "2")
    private Integer sectionCount;

    @Schema(description = "起始周次", example = "9")
    private Integer weekStart;

    @Schema(description = "结束周次", example = "13")
    private Integer weekEnd;

    @Schema(description = "周次类型：0=全部周，1=单周，2=双周", example = "0")
    private Integer weekOddEven;

    @Schema(description = "上课教室", example = "第一教学楼305")
    private String classroom;

    @Schema(description = "校区名称（识别不到可以为 null）", example = "清水河校区")
    private String campus;

    @Schema(description = "备注信息（如“第1-2周停课”之类，没有则为 null）")
    private String remark;

    @Schema(description = "原始时间表达式", example = "9-13周,连1-2节,第一教学楼305")
    private String rawTimeExpr;
}

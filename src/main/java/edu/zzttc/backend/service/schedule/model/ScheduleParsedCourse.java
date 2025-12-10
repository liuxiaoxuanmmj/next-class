package edu.zzttc.backend.service.schedule.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 大模型解析出的“课程基础信息”
 * 与 db_course 表结构大致对应
 */
@Data
@Schema(description = "课表解析后的课程基础信息")
public class ScheduleParsedCourse implements Serializable {

    @Schema(description = "课程名称", example = "网络安全攻防技术")
    private String courseName;

    @Schema(description = "课程代码 / 课程号", example = "R0902840.01")
    private String courseCode;

    @Schema(description = "任课教师姓名", example = "罗蓉成")
    private String teacherName;

    @Schema(description = "学分（不一定能识别出来，识别不到可以为 null）", example = "3.0")
    private Double credit;

    @Schema(description = "课程在课表上的颜色标签（十六进制，可选）", example = "#66CCFF")
    private String colorTag;
}


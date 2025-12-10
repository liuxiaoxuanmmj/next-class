package edu.zzttc.backend.service.schedule.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 大模型解析后的整学期课表结构
 */
@Data
@Schema(description = "大模型解析后的整学期课表结构")
public class ScheduleParsed implements Serializable {

    @Schema(description = "课程基础信息列表")
    private List<ScheduleParsedCourse> courses = new ArrayList<>();

    @Schema(description = "具体每一条上课安排")
    private List<ScheduleParsedItem> items = new ArrayList<>();

    @Schema(description = "模型识别到的原始文本（OCR 粗文本）")
    private String rawOcrText;

    @Schema(description = "课表图片路径或 URL")
    private String imageUrl;
}

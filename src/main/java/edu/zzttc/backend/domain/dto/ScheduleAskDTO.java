package edu.zzttc.backend.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@Schema(description = "课表问答请求参数")
public class ScheduleAskDTO {

    @Schema(description = "用户问题，如：我今天有什么课？", example = "我今天下午有什么课？")
    @NotBlank(message = "问题不能为空")
    private String question;

    @Schema(description = "查询日期，不传则默认今天", example = "2025-11-25")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
}

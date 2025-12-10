package edu.zzttc.backend.domain.vo.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "课表截图导入结果")
public class ScheduleUploadResultVO {

    @Schema(description = "学期ID")
    private Integer termId;

    @Schema(description = "本次导入新增/更新的课程数量")
    private Integer courseCount;

    @Schema(description = "本次导入新增的排课条数")
    private Integer itemCount;

    @Schema(description = "课表截图存储路径")
    private String imageUrl;
}

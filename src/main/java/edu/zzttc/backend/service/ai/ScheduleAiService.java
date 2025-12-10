package edu.zzttc.backend.service.ai;

import edu.zzttc.backend.domain.entity.TermConfig;
import edu.zzttc.backend.service.schedule.model.ScheduleParsed;

public interface ScheduleAiService {

    /**
     * 调用大模型，从课表截图中解析出结构化课表
     * @param imagePath 图片在服务器上的路径
     * @param termConfig 学期配置信息（学期名、开学日期等）
     */
    ScheduleParsed parseScheduleFromImage(String imagePath, TermConfig termConfig);
}

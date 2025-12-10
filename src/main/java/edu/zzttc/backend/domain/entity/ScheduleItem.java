package edu.zzttc.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("db_schedule_item")
public class ScheduleItem implements BaseData {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;
    private Integer termId;
    private Integer courseId;

    /** 1=周一 ... 7=周日 */
    private Integer dayOfWeek;

    /** 从第几节开始 */
    private Integer sectionStart;

    /** 连上几节 */
    private Integer sectionCount;

    /** 周次区间 */
    private Integer weekStart;
    private Integer weekEnd;

    /** 0=全部周，1=单周，2=双周 */
    private Integer weekOddEven;

    private String classroom;
    private String campus;
    private String remark;

    /** 原始时间表达式，如 "9-13,第x教学楼305" */
    private String rawTimeExpr;

    private Date createdAt;
    private Date updatedAt;
}

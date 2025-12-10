package edu.zzttc.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
@TableName("db_term_config")
public class TermConfig implements BaseData {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private String termName;

    /** 学期第一周周一的日期 */
    private LocalDate startDate;

    /** 总周数（可以为 null） */
    private Integer totalWeeks;

    private Date createdAt;
    private Date updatedAt;
}

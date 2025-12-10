package edu.zzttc.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("db_schedule_import")
public class ScheduleImport implements BaseData {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;
    private Integer termId;

    private String imageUrl;
    private String rawOcrText;
    private String parsedJson;
    private String status;

    private Date createdAt;
}

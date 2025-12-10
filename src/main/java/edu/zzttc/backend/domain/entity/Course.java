package edu.zzttc.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("db_course")
public class Course implements BaseData {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;
    private Integer termId;

    private String courseName;
    private String courseCode;
    private String teacherName;
    private Double credit;
    private String colorTag;

    private Date createdAt;
    private Date updatedAt;
}

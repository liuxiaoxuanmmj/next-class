package edu.zzttc.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("db_subscription_preference")
public class SubscriptionPreference implements BaseData {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private Boolean subscribed;
    private String timezone;
    private String dailyTime;
    private String channels;
    private Integer advanceMinutes;
    private LocalDate lastSentDate;
}

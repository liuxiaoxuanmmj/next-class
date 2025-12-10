package edu.zzttc.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zzttc.backend.domain.entity.ScheduleItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScheduleItemMapper extends BaseMapper<ScheduleItem> {
}

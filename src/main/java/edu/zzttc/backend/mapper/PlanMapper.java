package edu.zzttc.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zzttc.backend.domain.entity.Plan;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlanMapper extends BaseMapper<Plan> {}


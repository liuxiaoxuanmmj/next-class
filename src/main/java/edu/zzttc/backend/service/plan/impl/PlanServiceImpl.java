package edu.zzttc.backend.service.plan.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.zzttc.backend.domain.entity.Plan;
import edu.zzttc.backend.mapper.PlanMapper;
import edu.zzttc.backend.service.plan.PlanService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PlanServiceImpl implements PlanService {
    @Resource
    private PlanMapper mapper;

    @Override
    public void create(Plan plan) { mapper.insert(plan); }

    @Override
    public void update(Plan plan) { mapper.updateById(plan); }

    @Override
    public void delete(Integer id) { mapper.deleteById(id); }

    @Override
    public List<Plan> listByRange(Integer userId, LocalDateTime start, LocalDateTime end) {
        return mapper.selectList(Wrappers.<Plan>lambdaQuery()
                .eq(Plan::getUserId, userId)
                .ge(Plan::getStartTime, start)
                .le(Plan::getEndTime, end));
    }
}


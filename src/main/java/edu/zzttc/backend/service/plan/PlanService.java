package edu.zzttc.backend.service.plan;

import edu.zzttc.backend.domain.entity.Plan;

import java.time.LocalDateTime;
import java.util.List;

public interface PlanService {
    void create(Plan plan);
    void update(Plan plan);
    void delete(Integer id);
    List<Plan> listByRange(Integer userId, LocalDateTime start, LocalDateTime end);
}


package edu.zzttc.backend.controller;

import edu.zzttc.backend.domain.entity.Account;
import edu.zzttc.backend.domain.entity.Plan;
import edu.zzttc.backend.domain.entity.RestBean;
import edu.zzttc.backend.service.account.AccountService;
import edu.zzttc.backend.service.plan.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "计划管理接口")
@RestController
@RequestMapping("/api/plans")
public class PlanController {
    @Resource
    private PlanService planService;
    @Resource
    private AccountService accountService;

    @Operation(summary = "新增计划")
    @PostMapping
    public RestBean<Void> create(@RequestBody Plan plan) {
        plan.setUserId(currentUserId());
        planService.create(plan);
        return RestBean.success();
    }

    @Operation(summary = "修改计划")
    @PutMapping
    public RestBean<Void> update(@RequestBody Plan plan) {
        plan.setUserId(currentUserId());
        planService.update(plan);
        return RestBean.success();
    }

    @Operation(summary = "删除计划")
    @DeleteMapping("/{id}")
    public RestBean<Void> delete(@PathVariable Integer id) {
        planService.delete(id);
        return RestBean.success();
    }

    @Operation(summary = "时间范围查询计划")
    @GetMapping
    public RestBean<List<Plan>> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        Integer userId = currentUserId();
        return RestBean.success(planService.listByRange(userId, start, end));
    }

    private Integer currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Account account = accountService.findAccountByUsernameOrEmail(username);
        return account.getId();
    }
}


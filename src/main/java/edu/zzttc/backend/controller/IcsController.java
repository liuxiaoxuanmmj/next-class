package edu.zzttc.backend.controller;

import edu.zzttc.backend.domain.entity.Account;
import edu.zzttc.backend.domain.entity.RestBean;
import edu.zzttc.backend.service.account.AccountService;
import edu.zzttc.backend.service.ics.IcsService;
import edu.zzttc.backend.service.plan.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "ICS 导入导出")
@RestController
@RequestMapping("/api/ics")
public class IcsController {
    @Resource
    private PlanService planService;
    @Resource
    private AccountService accountService;

    @Operation(summary = "导出计划为 ICS")
    @GetMapping("/export")
    public RestBean<String> export(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        Integer userId = currentUserId();
        IcsService ics = new IcsService();
        String content = ics.exportPlans(planService.listByRange(userId, start, end));
        return RestBean.success(content);
    }

    private Integer currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Account account = accountService.findAccountByUsernameOrEmail(username);
        return account.getId();
    }
}


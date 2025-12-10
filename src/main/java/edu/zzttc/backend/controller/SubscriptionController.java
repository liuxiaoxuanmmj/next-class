package edu.zzttc.backend.controller;

import edu.zzttc.backend.domain.entity.Account;
import edu.zzttc.backend.domain.entity.RestBean;
import edu.zzttc.backend.domain.entity.SubscriptionPreference;
import edu.zzttc.backend.service.account.AccountService;
import edu.zzttc.backend.service.subscription.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订阅设置接口")
@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    @Resource
    private SubscriptionService subscriptionService;
    @Resource
    private AccountService accountService;

    @Operation(summary = "开启订阅")
    @PostMapping("/subscribe")
    public RestBean<Void> subscribe(@RequestBody SubscriptionPreference pref) {
        Integer userId = currentUserId();
        subscriptionService.subscribe(userId, pref);
        return RestBean.success();
    }

    @Operation(summary = "取消订阅")
    @PostMapping("/unsubscribe")
    public RestBean<Void> unsubscribe() {
        Integer userId = currentUserId();
        subscriptionService.unsubscribe(userId);
        return RestBean.success();
    }

    @Operation(summary = "查询订阅偏好")
    @GetMapping("/preferences")
    public RestBean<SubscriptionPreference> preferences() {
        Integer userId = currentUserId();
        return RestBean.success(subscriptionService.get(userId));
    }

    private Integer currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Account account = accountService.findAccountByUsernameOrEmail(username);
        return account.getId();
    }
}


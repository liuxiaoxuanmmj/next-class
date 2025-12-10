package edu.zzttc.backend.controller;

import edu.zzttc.backend.domain.dto.ConfirmResetDTO;
import edu.zzttc.backend.domain.dto.EmailRegisterDTO;
import edu.zzttc.backend.domain.dto.EmailResetDTO;
import edu.zzttc.backend.domain.entity.RestBean;
import edu.zzttc.backend.domain.vo.AuthorizeVO;
import edu.zzttc.backend.service.account.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.function.Function;
import java.util.function.Supplier;

@Tag(name = "认证模块", description = "登录、注册、邮箱验证码等接口")
@RestController
@Validated
@RequestMapping("/api/auth")
public class AuthorizeController {

    @Resource
    AccountService accountService;

    @Operation(
            summary = "用户名密码登录（由 Spring Security 处理）",
            description = """
                    使用表单参数 username / password 登录。
                    实际认证逻辑由 Spring Security 的 formLogin + JwtAuthorizeFilter 完成，
                    本方法主要用于在 Swagger / Knife4j 中生成接口文档，便于调试。
                    """
    )
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public RestBean<AuthorizeVO> login(
            @Parameter(description = "用户名或邮箱", required = true)
            @RequestParam String username,
            @Parameter(description = "密码", required = true)
            @RequestParam String password
    ) {
        // Spring Security 的 UsernamePasswordAuthenticationFilter 优先拦截 /api/auth/login 并完成认证。
        throw new UnsupportedOperationException("登录由 Spring Security 处理，这个方法仅用于生成 OpenAPI 文档");
    }


    @Operation(
            summary = "获取邮箱验证码",
            description = "根据 type 发送验证码，type 可选：register（注册）、reset（重置密码）"
    )
    @GetMapping("/ask-code")
    public RestBean<Void> askVerifyCode(
            @Parameter(description = "邮箱地址", example = "student@stu.xxx.edu.cn")
            @RequestParam @Email String email,
            @Parameter(description = "用途：register=注册，reset=重置密码", example = "register")
            @RequestParam @Pattern(regexp = "(register|reset)") String type,
            HttpServletRequest request) {

        return this.messageHandle(() ->
                accountService.registerEamilVerifyCode(type, email, request.getRemoteAddr()));
    }

    @Operation(summary = "邮箱注册", description = "使用邮箱 + 验证码完成账号注册")
    @PostMapping("/register")
    public RestBean<Void> register(
            @Valid @RequestBody EmailRegisterDTO accountDTO) {

        return this.messageHandle(() ->
                accountService.registerEmailAccount(accountDTO));
    }

    @Operation(summary = "校验重置验证码", description = "验证邮箱重置验证码是否正确")
    @PostMapping("/reset-confirm")
    public RestBean<Void> resetConfirm(
            @RequestBody @Valid ConfirmResetDTO dto) {

        return this.messageHandle(dto, accountService::resetConfirm);
    }

    @Operation(summary = "重置密码", description = "验证码校验通过后，重置账号密码")
    @PostMapping("/reset-password")
    public RestBean<Void> resetPassword(
            @RequestBody @Valid EmailResetDTO dto) {

        return this.messageHandle(dto, accountService::resetEmailAccountPassword);
    }

    private <T> RestBean<Void> messageHandle(T dto, Function<T, String> function) {
        return messageHandle(() -> function.apply(dto));
    }

    private RestBean<Void> messageHandle(Supplier<String> action) {
        String message = action.get();
        return message == null ? RestBean.success()
                : RestBean.failure(400, message);
    }
}

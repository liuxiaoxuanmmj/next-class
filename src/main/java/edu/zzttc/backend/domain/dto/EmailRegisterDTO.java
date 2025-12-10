package edu.zzttc.backend.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "邮箱注册请求参数")
public class EmailRegisterDTO {

    @Schema(description = "邮箱地址", example = "student@stu.ustc.edu.cn")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "用户名（中英文或数字）", example = "小明")
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9\\u4e00-\\u9fa5]+$", message = "用户名只能包含中英文或数字")
    @Length(min = 1, max = 10, message = "用户名长度 1-10 位")
    private String username;

    @Schema(description = "密码（6-20 位）", example = "Passw0rd")
    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 20, message = "密码长度 6-20 位")
    private String password;

    @Schema(description = "邮箱验证码（6 位数字）", example = "123456")
    @NotBlank(message = "验证码不能为空")
    @Length(min = 6, max = 6, message = "验证码长度必须为 6 位")
    private String code;
}


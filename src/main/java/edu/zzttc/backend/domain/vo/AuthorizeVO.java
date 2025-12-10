package edu.zzttc.backend.domain.vo;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "登录成功返回数据")
public class AuthorizeVO {

    @Schema(description = "用户名", example = "小明")
    private String username;

    @Schema(description = "角色", example = "ROLE_admin")
    private String role;

    @Schema(description = "JWT 访问令牌")
    private String token;

    @Schema(description = "token 过期时间")
    private Date expires;

    @Schema(description = "邮箱", example = "student@stu.xxx.edu.cn")
    private String email;
}

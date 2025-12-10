package edu.zzttc.backend.service.account;

import com.baomidou.mybatisplus.extension.service.IService;
import edu.zzttc.backend.domain.dto.ConfirmResetDTO;
import edu.zzttc.backend.domain.dto.EmailRegisterDTO;
import edu.zzttc.backend.domain.dto.EmailResetDTO;
import edu.zzttc.backend.domain.entity.Account;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface AccountService extends IService<Account>, UserDetailsService {
    // 通过用户名或邮箱查询用户
    Account findAccountByUsernameOrEmail(String text);

    // 邮件验证码发送
    String registerEamilVerifyCode(String type, String email, String ip);

    // 邮件注册账号
    String registerEmailAccount(EmailRegisterDTO dto);

    // 重置密码验证
    String resetConfirm(ConfirmResetDTO dto);

    // 重置密码
    String resetEmailAccountPassword(EmailResetDTO dto);
}

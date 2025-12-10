package edu.zzttc.backend.service.account.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import edu.zzttc.backend.domain.dto.ConfirmResetDTO;
import edu.zzttc.backend.domain.dto.EmailRegisterDTO;
import edu.zzttc.backend.domain.dto.EmailResetDTO;
import edu.zzttc.backend.domain.entity.Account;
import edu.zzttc.backend.mapper.AccountMapper;
import edu.zzttc.backend.service.account.AccountService;

import edu.zzttc.backend.utils.Const;
import edu.zzttc.backend.utils.FlowUtils;
import jakarta.annotation.Resource;
import org.springframework.amqp.core.AmqpTemplate;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;


@Service
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    @Resource
    AmqpTemplate amqpTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    FlowUtils utils;
    @Resource
    PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = this.findAccountByUsernameOrEmail(username);
        if (account == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        return User.withUsername(username).password(account.getPassword()).roles(account.getRole()).build();
    }

    public Account findAccountByUsernameOrEmail(String text) {
        return this.query()
                .eq("username",text).or()
                .eq("email",text)
                .one();
    }

    @Override
    public String registerEamilVerifyCode(String type, String email, String ip) {
        synchronized (ip.intern()) {
            if (!this.verifyLimit(ip)) {
                return "请求频繁，请稍后重试";
            }
            Random random = new Random();
            int code = random.nextInt(899999) + 100000;
            Map<String, Object> data = Map.of("type", type, "email", email, "code", code);
            amqpTemplate.convertAndSend("email", data);
            stringRedisTemplate.opsForValue()
                    .set(Const.VERIFY_EMAIL_DATA + email, String.valueOf(code), 3, TimeUnit.MINUTES);
            return null;
        }
    }

    @Override
    public String registerEmailAccount(EmailRegisterDTO dto) {
        String email = dto.getEmail();
        String username = dto.getUsername();

        // 从 Redis 获取验证码
        String redisKey = Const.VERIFY_EMAIL_DATA + email;
        String code = stringRedisTemplate.opsForValue().get(redisKey);
        System.out.println("🔍 [Debug] Redis Key = [" + redisKey + "], Code = [" + code + "]");
        // 校验验证码存在性
        if (code == null) {
            return "请先获取验证码";
        }

        // 校验验证码是否匹配
        if (!code.equals(dto.getCode())) {
            return "验证码错误，请重新输入";
        }

        // 校验邮箱是否重复
        if (existsAccountByEmail(email)) {
            return "此邮箱已注册过账号";
        }

        // 校验用户名是否重复
        if (existsAccountByUsername(username)) {
            return "此用户名已注册过账号";
        }

        // 保存新账户
        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        Account account = new Account(null, username, encodedPassword, email, "user", new Date());

        if (save(account)) {
            // 注册成功后删除 Redis 验证码，防止复用
            stringRedisTemplate.delete(redisKey);
            return null; // 成功
        } else {
            return "内部错误，请联系管理员";
        }
    }

    @Override
    public String resetConfirm(ConfirmResetDTO dto) {
        String email = dto.getEmail();
        String code = stringRedisTemplate.opsForValue().get(Const.VERIFY_EMAIL_DATA + email);
        if (code == null) {
            return "请先获取验证码";
        }
        if(!code.equals(dto.getCode())) {
            return "验证码错误，请重新输入";
        }
        return null;
    }

    @Override
    public String resetEmailAccountPassword(EmailResetDTO dto) {
        String email = dto.getEmail();
        String verify = this.resetConfirm(new ConfirmResetDTO(email,dto.getCode()));
        if(verify != null) {
            return verify;
        }
        String password = passwordEncoder.encode(dto.getPassword());
        boolean update = this.update().eq("email", email).set("password", password).update();
        if(update) {
            stringRedisTemplate.delete(Const.VERIFY_EMAIL_DATA + email);
        }
        return null;
    }


    private boolean existsAccountByEmail(String email){
        return this.baseMapper.exists(Wrappers.<Account>query().eq("email",email));
    }

    private boolean existsAccountByUsername(String username){
        return this.baseMapper.exists(Wrappers.<Account>query().eq("username",username));
    }

    private boolean verifyLimit(String address){
        String key = Const.VERIFY_EMAIL_LIMIT + address;
        return utils.limitOnceCheck(key,60);
    }


}

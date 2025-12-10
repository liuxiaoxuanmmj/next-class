package edu.zzttc.backend.listener;

import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import edu.zzttc.backend.utils.TemplateRenderer;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RabbitListener(queues = "email")
public class MailQueueListener {
    @Resource
    JavaMailSender sender;

    @Value("${spring.mail.username}")
    String username;

    @Value("${spring.mail.from-name:系统通知}")
    String fromName;

    @RabbitHandler
    public void sendMailMessage(Map<String, Object> data) {
        String email = (String) data.get("email");
        String type = (String) data.get("type");
        SimpleMailMessage message = null;
        MimeMessage htmlMessage = null;
        if ("register".equals(type)) {
            Integer code = (Integer) data.get("code");
            message = createMailMessage("欢迎注册", "您的邮件验证码为：" + code + ",有效时间为三分钟", email);
        } else if ("reset".equals(type)) {
            Integer code = (Integer) data.get("code");
            message = createMailMessage("您的密码重置邮件", "您好，您正在进行重置密码操作，验证码为：" + code + "有效时间三分钟", email);
        } else if ("digest".equals(type)) {
            Boolean useHtml = (Boolean) data.getOrDefault("useHtml", Boolean.FALSE);
            if (Boolean.TRUE.equals(useHtml)) {
                String date = (String) data.get("date");
                String itemsHtml = (String) data.get("itemsHtml");
                String plansHtml = (String) data.get("plansHtml");
                String html = TemplateRenderer.render("templates/email/digest.html", Map.of(
                        "date", date == null ? "" : date,
                        "items", itemsHtml == null ? "" : itemsHtml,
                        "plans", plansHtml == null ? "" : plansHtml));
                if (html != null && !html.isEmpty()) {
                    try {
                        htmlMessage = sender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(htmlMessage, "UTF-8");
                        helper.setSubject("今日课表提醒");
                        helper.setTo(email);
                        helper.setFrom(username, fromName);
                        helper.setText(html, true);
                    } catch (Exception e) {
                        // 渲染或组装失败则回退到纯文本
                        String content = (String) data.get("content");
                        message = createMailMessage("今日课表提醒", content, email);
                    }
                } else {
                    String content = (String) data.get("content");
                    message = createMailMessage("今日课表提醒", content, email);
                }
            } else {
                String content = (String) data.get("content");
                message = createMailMessage("今日课表提醒", content, email);
            }
        } else {
            message = null;
        }
        if (htmlMessage != null) {
            sender.send(htmlMessage);
            return;
        }
        if (message == null) {
            return;
        }
        sender.send(message);
    }

    private SimpleMailMessage createMailMessage(String title, String content, String email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setSubject(title);
        message.setText(content);
        message.setTo(email);
        String from = String.format("%s <%s>", fromName, username);
        message.setFrom(from);

        return message;
    }
}

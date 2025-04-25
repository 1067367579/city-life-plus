package com.hmdp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
//邮件发送服务
public class EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.from}")
    private String from;

    //邮件发送方法
    public void sendSimpleMail(String to,String code) {
        log.info("负责发送的邮箱为：{}",from);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("城市生活通平台验证码");
        message.setText("尊敬的用户您好!\n\n" +
        "感谢您使用城市生活通平台。\n\n" +
                        "尊敬的" + to + "，您的校验验证码为: " + code +
                        "， 有效期2分钟，请不要把验证码信息泄露给其他人,如非本人请勿操作");
        javaMailSender.send(message);
    }
}

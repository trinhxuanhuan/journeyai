package com.vietkhampha.authservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean emailEnabled;
    private final String fromAddress;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.email.enabled}") boolean emailEnabled,
            @Value("${app.email.from-address}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.emailEnabled = emailEnabled;
        this.fromAddress = fromAddress;
    }

    public void sendOtpEmail(String toEmail, String otpCode) {
        if (!emailEnabled) {
            log.info("[DEV MODE - EMAIL_ENABLED=false] Ma OTP cho {}: {}", toEmail, otpCode);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Ma xac thuc tai khoan Viet Kham Pha");
        message.setText("Ma OTP cua ban la: " + otpCode + "\nMa co hieu luc trong 5 phut.");
        mailSender.send(message);
    }
}
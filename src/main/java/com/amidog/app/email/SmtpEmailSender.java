package com.amidog.app.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "amidog.email.delivery", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    public SmtpEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.text());
        if (message.replyTo() != null && !message.replyTo().isBlank()) {
            mail.setReplyTo(message.replyTo());
        }
        mailSender.send(mail);
    }
}

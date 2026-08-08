package com.amidog.app.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@ConditionalOnProperty(name = "amidog.email.delivery", havingValue = "smtp")
public class SmtpTransportConfiguration {

    @Bean
    JavaMailSender javaMailSender(AmidogProperties properties) {
        AmidogProperties.Smtp settings = properties.email().smtp();
        if (settings == null) {
            throw new IllegalStateException("SMTP settings are required when EMAIL_DELIVERY=smtp");
        }
        settings.validateForDelivery();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.host());
        sender.setPort(settings.port());
        if (settings.auth()) {
            sender.setUsername(settings.username());
            sender.setPassword(settings.password());
        }
        sender.setJavaMailProperties(javaMailProperties(settings));
        return sender;
    }

    static Properties javaMailProperties(AmidogProperties.Smtp settings) {
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.auth", Boolean.toString(settings.auth()));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(settings.starttls()));
        properties.setProperty("mail.smtp.starttls.required", Boolean.toString(settings.starttlsRequired()));
        properties.setProperty("mail.smtp.ssl.enable", Boolean.toString(settings.ssl()));
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        return properties;
    }
}

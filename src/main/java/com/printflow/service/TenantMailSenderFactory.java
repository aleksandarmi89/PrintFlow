package com.printflow.service;

import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class TenantMailSenderFactory {

    public JavaMailSender buildSender(String host, Integer port, String username, String password, boolean useTls, boolean useSsl) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(resolvePort(port, useSsl, useTls));
        if (username != null && !username.isBlank()) {
            sender.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            sender.setPassword(password);
        }
        Properties props = new Properties();
        boolean auth = username != null && !username.isBlank();
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(useTls));
        props.put("mail.smtp.ssl.enable", String.valueOf(useSsl));
        sender.setJavaMailProperties(props);
        return sender;
    }

    private int resolvePort(Integer port, boolean useSsl, boolean useTls) {
        if (port != null && port > 0) {
            return port;
        }
        if (useSsl) {
            return 465;
        }
        if (useTls) {
            return 587;
        }
        return 25;
    }
}

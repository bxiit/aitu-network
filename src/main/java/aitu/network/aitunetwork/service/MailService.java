package aitu.network.aitunetwork.service;

import aitu.network.aitunetwork.model.entity.User;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender mailSender;
    private final Configuration freemarkerConfig;
    // Gmail SMTP only sends from the authenticated account
    @Value("${spring.mail.username}")
    private String from;

    public void sendConfirmationMessage(User user) {
        MimeMessage message = mailSender.createMimeMessage();

        try {
            Map<String, Object> model = new HashMap<>();
            model.put("name", user.getUsername());
            // a code to enter on the site rather than a link: the backend doesn't know the site's public address
            model.put("code", user.getVerificationTokenHolder().token());

            Template template = freemarkerConfig.getTemplate("confirmation_email.ftl");
            StringWriter writer = new StringWriter();
            template.process(model, writer);
            String html = writer.toString();

            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(user.getEmail());
            helper.setSubject("Confirm your email");
            helper.setText(html, true);

            mailSender.send(message);
        } catch (MessagingException | IOException | TemplateException e) {
            throw new RuntimeException("Failed to send confirmation email", e);
        }
    }

    public void sendForgotPasswordMessage(User user) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            Map<String, Object> model = new HashMap<>();
            model.put("name", user.getUsername());
            model.put("token", user.getRecoverTokenHolder().token());
            Template template = freemarkerConfig.getTemplate("forgot_password.ftl");
            StringWriter writer = new StringWriter();
            template.process(model, writer);
            String html = writer.toString();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(from);
            helper.setTo(user.getEmail());
            helper.setSubject("Confirm your email");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (IOException | TemplateException | MessagingException e) {
            throw new RuntimeException("Failed to send confirmation email", e);
        }
    }
}

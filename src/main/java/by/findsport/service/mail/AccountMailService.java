package by.findsport.service.mail;

import by.findsport.config.AuthProperties;
import by.findsport.domain.Language;
import by.findsport.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Письма учетной записи на языке пользователя (ТЗ п. 4.1.6, 4.2.2).
 * Недоступность почтового сервиса не прерывает основной сценарий: ошибка журналируется,
 * письмо можно запросить повторно (п. 4.2.16).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountMailService {

    private final MailSender mailSender;
    private final AuthProperties properties;

    /** @return {@code true}, если письмо передано почтовому сервису */
    public boolean sendEmailVerification(User user, String token) {
        String link = link("/verify-email.html", token);
        return user.getPreferredLanguage() == Language.EN
                ? send(user, "FindSport: confirm your e-mail", """
                        Hello!

                        You have registered on FindSport. To activate your account, confirm your e-mail:
                        %s

                        The link is valid for %d hours. If you did not register, just ignore this message.
                        """.formatted(link, properties.emailVerificationTtl().toHours()))
                : send(user, "FindSport: подтвердите e-mail", """
                        Здравствуйте!

                        Вы зарегистрировались на платформе FindSport. Чтобы активировать учетную запись, \
                        подтвердите адрес электронной почты:
                        %s

                        Ссылка действует %d ч. Если вы не регистрировались, просто проигнорируйте это письмо.
                        """.formatted(link, properties.emailVerificationTtl().toHours()));
    }

    public boolean sendPasswordReset(User user, String token) {
        String link = link("/reset-password.html", token);
        return user.getPreferredLanguage() == Language.EN
                ? send(user, "FindSport: password recovery", """
                        Hello!

                        We received a request to restore access to your FindSport account. Set a new password:
                        %s

                        The link is valid for %d minutes and can be used once. If you did not request it, \
                        ignore this message — your password stays the same.
                        """.formatted(link, properties.passwordResetTtl().toMinutes()))
                : send(user, "FindSport: восстановление доступа", """
                        Здравствуйте!

                        Мы получили запрос на восстановление доступа к учетной записи FindSport. \
                        Задайте новый пароль по ссылке:
                        %s

                        Ссылка действует %d мин. и может быть использована один раз. Если вы не запрашивали \
                        восстановление, проигнорируйте письмо — пароль останется прежним.
                        """.formatted(link, properties.passwordResetTtl().toMinutes()));
    }

    private String link(String page, String token) {
        return UriComponentsBuilder.fromUriString(properties.publicBaseUrl())
                .path(page)
                .queryParam("token", token)
                .toUriString();
    }

    private boolean send(User user, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.error("Почтовый сервис недоступен, письмо «{}» для userId={} не отправлено: {}",
                    subject, user.getId(), e.getMessage());
            return false;
        }
    }
}

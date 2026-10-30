package by.findsport;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

/**
 * Почтовый сервис для тестов: запоминает письма вместо отправки и умеет имитировать
 * недоступность SMTP (ТЗ п. 4.2.16).
 */
public class CapturingMailSender implements MailSender {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private final List<SimpleMailMessage> sent = new ArrayList<>();
    private boolean unavailable;

    @Override
    public synchronized void send(SimpleMailMessage... messages) {
        if (unavailable) {
            throw new MailSendException("SMTP недоступен (тест)");
        }
        sent.addAll(List.of(messages));
    }

    public synchronized List<SimpleMailMessage> sent() {
        return List.copyOf(sent);
    }

    /** Токен из ссылки в последнем письме, адресованном {@code email}. */
    public synchronized String lastTokenFor(String email) {
        SimpleMailMessage message = sent.reversed().stream()
                .filter(candidate -> List.of(candidate.getTo()).contains(email))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Письмо для " + email + " не отправлялось"));
        Matcher matcher = TOKEN.matcher(message.getText());
        if (!matcher.find()) {
            throw new AssertionError("В письме нет ссылки с токеном");
        }
        return matcher.group(1);
    }

    public synchronized void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    public synchronized void reset() {
        sent.clear();
        unavailable = false;
    }
}

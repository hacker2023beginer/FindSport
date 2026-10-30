package by.findsport.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Параметры регистрации, входа и восстановления доступа.
 *
 * @param maxFailedLogins         число последовательных неуспешных входов до ограничения (п. 4.2.3)
 * @param lockoutDuration         длительность временного ограничения входа (п. 4.2.3)
 * @param adminSessionTimeout     предельная неактивность административной сессии (п. 4.2.4)
 * @param emailVerificationTtl    срок действия ссылки подтверждения e-mail
 * @param passwordResetTtl        срок действия ссылки восстановления доступа
 * @param publicBaseUrl           адрес веб-интерфейса для ссылок в письмах
 * @param mailFrom                адрес отправителя писем
 * @param publicForms             ограничение частоты отправки публичных форм (п. 4.2.14)
 */
@ConfigurationProperties("findsport.auth")
public record AuthProperties(
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15m") Duration lockoutDuration,
        @DefaultValue("30m") Duration adminSessionTimeout,
        @DefaultValue("24h") Duration emailVerificationTtl,
        @DefaultValue("1h") Duration passwordResetTtl,
        @DefaultValue("http://localhost:8080") String publicBaseUrl,
        @DefaultValue("no-reply@findsport.by") String mailFrom,
        @DefaultValue PublicForms publicForms) {

    /**
     * Более {@code maxRequests} отправок формы за {@code window} с одного адреса блокируют ее на {@code block}.
     */
    public record PublicForms(
            @DefaultValue("5") int maxRequests,
            @DefaultValue("1m") Duration window,
            @DefaultValue("1h") Duration block) {
    }
}

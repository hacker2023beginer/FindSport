package by.findsport.service;

import by.findsport.domain.TokenPurpose;
import by.findsport.domain.User;
import by.findsport.domain.UserToken;
import by.findsport.repository.UserRepository;
import by.findsport.repository.UserTokenRepository;
import by.findsport.service.exception.InvalidTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Одноразовые ссылки из писем. Токен — 256 случайных бит; в БД хранится только его SHA-256,
 * поэтому утечка таблицы не дает рабочих ссылок (ТЗ п. 4.13.13).
 */
@Component
@RequiredArgsConstructor
class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserTokenRepository tokens;
    private final UserRepository users;
    private final Clock clock;

    /** Выдает новую ссылку и отменяет ранее выданные ссылки того же назначения. */
    String issue(User user, TokenPurpose purpose, Duration ttl) {
        Instant now = clock.instant();
        tokens.invalidateActive(user.getId(), purpose, now);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new UserToken(user, purpose, hash(token), now.plus(ttl)));
        return token;
    }

    /**
     * Погашает ссылку и возвращает ее владельца. Владелец перечитывается: массовые обновления
     * ({@code @Modifying}) очищают контекст, и ссылка токена может указывать на отсоединенный экземпляр.
     */
    User consume(String token, TokenPurpose purpose) {
        Instant now = clock.instant();
        UserToken stored = tokens.findByTokenHashAndPurpose(hash(token), purpose)
                .filter(candidate -> candidate.isUsableAt(now))
                .orElseThrow(() -> new InvalidTokenException("Ссылка недействительна или срок ее действия истек"));
        stored.markUsed(now);
        return users.findById(stored.getUser().getId()).orElseThrow();
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}

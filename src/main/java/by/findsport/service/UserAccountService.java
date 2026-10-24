package by.findsport.service;

import by.findsport.domain.User;
import by.findsport.repository.UserRepository;
import by.findsport.service.exception.NotFoundException;
import java.time.Clock;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Загрузка учетных записей для Spring Security и разрешение текущего пользователя в сервисах.
 * Вход возможен только после подтверждения e-mail (ТЗ п. 4.2.2), для незаблокированной (п. 4.5.1)
 * и не ограниченной временно учетной записи (п. 4.2.3).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAccountService implements UserDetailsService {

    private final UserRepository users;
    private final Clock clock;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = users.findByEmail(normalize(email))
                .orElseThrow(() -> new UsernameNotFoundException("Учетная запись не найдена"));
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .disabled(user.isBlocked() || !user.isEmailVerified())
                .accountLocked(user.isLockedAt(clock.instant()))
                .build();
    }

    public User requireByEmail(String email) {
        return users.findByEmail(normalize(email))
                .orElseThrow(() -> new NotFoundException("Учетная запись не найдена"));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}

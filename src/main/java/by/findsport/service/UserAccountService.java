package by.findsport.service;

import by.findsport.domain.User;
import by.findsport.repository.UserRepository;
import by.findsport.service.exception.NotFoundException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Разрешение текущего пользователя (имя аутентификации — e-mail) в сервисах. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAccountService {

    private final UserRepository users;

    public User requireByEmail(String email) {
        return users.findByEmail(normalize(email))
                .orElseThrow(() -> new NotFoundException("Учетная запись не найдена"));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}

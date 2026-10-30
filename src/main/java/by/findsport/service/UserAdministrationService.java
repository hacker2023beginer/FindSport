package by.findsport.service;

import by.findsport.domain.User;
import by.findsport.domain.UserRole;
import by.findsport.repository.UserRepository;
import by.findsport.service.exception.ConflictException;
import by.findsport.service.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Административная часть: учетные записи и роли (ТЗ п. 4.5.1, 4.5.2, Приложение 2 «Управление ролями»).
 * Администратор не может снять с себя роль или заблокировать себя — иначе система может остаться без администратора.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserAdministrationService {

    private final UserRepository users;
    private final UserAccountService accounts;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAudit audit;

    @Transactional(readOnly = true)
    public Page<User> list(int page, int size) {
        return users.findAllByOrderByIdAsc(PageRequest.of(page, size));
    }

    public User changeRole(String adminEmail, Long userId, UserRole role) {
        User admin = accounts.requireByEmail(adminEmail);
        User target = requireUser(userId);
        rejectSelf(admin, target, "Нельзя изменить собственную роль");
        UserRole previous = target.getRole();
        if (previous != role) {
            target.setRole(role);
            audit.roleChanged(admin, target, previous);
        }
        return target;
    }

    /** Блокировка и разблокировка (п. 4.5.1); разблокировка также снимает временное ограничение входа. */
    public User setBlocked(String adminEmail, Long userId, boolean blocked) {
        User admin = accounts.requireByEmail(adminEmail);
        User target = requireUser(userId);
        rejectSelf(admin, target, "Нельзя заблокировать собственную учетную запись");
        if (target.isBlocked() != blocked) {
            target.setBlocked(blocked);
            if (!blocked) {
                target.clearLoginFailures();
            }
            audit.blockChanged(admin, target);
        }
        return target;
    }

    /**
     * Первичный администратор из конфигурации окружения: без него назначить роли некому.
     * Существующая учетная запись не изменяется.
     */
    public void ensureAdministrator(String email, String password) {
        if (users.existsByEmail(email)) {
            return;
        }
        User admin = new User(email, passwordEncoder.encode(password), UserRole.ADMIN);
        admin.setEmailVerified(true);
        users.save(admin);
        log.info("Создан первичный администратор userId={}", admin.getId());
    }

    private User requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Учетная запись не найдена"));
    }

    private static void rejectSelf(User admin, User target, String message) {
        if (admin.getId().equals(target.getId())) {
            throw new ConflictException(message);
        }
    }
}

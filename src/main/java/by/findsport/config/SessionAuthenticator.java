package by.findsport.config;

import by.findsport.domain.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Открывает серверную сессию после успешной проверки учетных данных. Имя аутентификации — e-mail,
 * полномочие — {@code ROLE_<роль>}. Сессии модератора и администратора (административная часть, п. 4.5)
 * завершаются после {@code findsport.auth.admin-session-timeout} неактивности (п. 4.2.4).
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticator {

    private static final Set<UserRole> ADMINISTRATIVE_ROLES = EnumSet.of(UserRole.MODERATOR, UserRole.ADMIN);

    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final AuthProperties properties;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public void signIn(String email, UserRole role, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        sessionStrategy.onAuthentication(authentication, request, response);
        CsrfCookieFilter.issueToken(request);
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        contexts.saveContext(context, request, response);
        if (ADMINISTRATIVE_ROLES.contains(role)) {
            request.getSession().setMaxInactiveInterval((int) properties.adminSessionTimeout().toSeconds());
        }
    }
}

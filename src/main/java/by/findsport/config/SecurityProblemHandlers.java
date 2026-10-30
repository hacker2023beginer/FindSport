package by.findsport.config;

import by.findsport.service.SecurityAudit;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * Отказы фильтров безопасности в едином формате RFC 7807, как и ошибки контроллеров:
 * нет сессии — 401, нет полномочий или CSRF-токена — 403. Также журналирует выход.
 */
@Component
@RequiredArgsConstructor
public class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler, LogoutHandler {

    private final ObjectMapper json;
    private final SecurityAudit audit;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Требуется вход в систему, или сессия завершена");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, e instanceof CsrfException
                ? "Отсутствует или устарел CSRF-токен. Обновите страницу и повторите действие"
                : "Недостаточно прав для выполнения операции");
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        audit.loggedOut(authentication == null ? null : authentication.getName());
    }

    private void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
    }
}

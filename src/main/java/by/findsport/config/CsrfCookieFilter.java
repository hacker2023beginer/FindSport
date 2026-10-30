package by.findsport.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security загружает CSRF-токен лениво; веб-интерфейсу же cookie {@code XSRF-TOKEN} нужна
 * уже после первого ответа, до первой отправки формы. Фильтр материализует токен на каждом запросе.
 */
class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        issueToken(request);
        chain.doFilter(request, response);
    }

    /** Загрузка токена записывает cookie, если ее еще нет или токен был заменен. */
    static void issueToken(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
    }
}

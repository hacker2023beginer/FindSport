package by.findsport.config;

import by.findsport.service.SecurityAudit;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Защита публичных форм от автоматизированных отправок (ТЗ п. 4.2.14): более
 * {@code max-requests} отправок одной формы за {@code window} с одного адреса блокируют эту форму
 * для адреса на {@code block} с информативным сообщением (HTTP 429, заголовок {@code Retry-After}).
 * <p>
 * Счетчики хранятся в памяти экземпляра; при горизонтальном масштабировании (п. 4.1.13) их нужно
 * вынести в общее хранилище.
 */
@Component
public class PublicFormRateLimitFilter extends OncePerRequestFilter {

    /** Публичные формы, доступные гостю. */
    static final Set<String> PUBLIC_FORMS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/email-verification/resend",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset");

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final AuthProperties.PublicForms limits;
    private final Clock clock;
    private final ObjectMapper json;
    private final SecurityAudit audit;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public PublicFormRateLimitFilter(AuthProperties properties, Clock clock, ObjectMapper json, SecurityAudit audit) {
        this.limits = properties.publicForms();
        this.clock = clock;
        this.json = json;
        this.audit = audit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !PUBLIC_FORMS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = clock.instant();
        String form = request.getRequestURI();
        Window window = windows.computeIfAbsent(request.getRemoteAddr() + ' ' + form, key -> new Window(now));
        Instant blockedUntil = window.register(now, limits);
        if (blockedUntil != null) {
            if (window.justBlocked) {
                audit.rateLimited(form);
            }
            reject(response, Duration.between(now, blockedUntil));
            return;
        }
        cleanupIfNeeded(now);
        chain.doFilter(request, response);
    }

    /** Сброс счетчиков — для тестов и ручной разблокировки. */
    public void reset() {
        windows.clear();
    }

    private void reject(HttpServletResponse response, Duration retryAfter) throws IOException {
        long minutes = Math.max(1, retryAfter.toMinutes());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Слишком много запросов. Форма временно заблокирована, повторите попытку через " + minutes + " мин.");
        problem.setTitle("Превышена частота запросов");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfter.toSeconds())));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), problem);
    }

    private void cleanupIfNeeded(Instant now) {
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> window.isStale(now, limits));
        }
    }

    /** Фиксированное окно подсчета отправок одной формы с одного адреса. */
    private static final class Window {

        private Instant start;
        private int count;
        private Instant blockedUntil;
        private boolean justBlocked;

        Window(Instant start) {
            this.start = start;
        }

        /** @return момент окончания блокировки, если запрос отклонен, иначе {@code null} */
        synchronized Instant register(Instant now, AuthProperties.PublicForms limits) {
            justBlocked = false;
            if (blockedUntil != null) {
                if (now.isBefore(blockedUntil)) {
                    return blockedUntil;
                }
                blockedUntil = null;
                start = now;
                count = 0;
            }
            if (!now.isBefore(start.plus(limits.window()))) {
                start = now;
                count = 0;
            }
            count++;
            if (count > limits.maxRequests()) {
                blockedUntil = now.plus(limits.block());
                justBlocked = true;
                return blockedUntil;
            }
            return null;
        }

        synchronized boolean isStale(Instant now, AuthProperties.PublicForms limits) {
            return (blockedUntil == null || !now.isBefore(blockedUntil))
                    && !now.isBefore(start.plus(limits.window()));
        }
    }
}

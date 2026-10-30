package by.findsport.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import by.findsport.service.SecurityAudit;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Модульная проверка ограничения частоты публичных форм (ТЗ п. 4.2.14): окно 1 мин, блокировка 1 ч. */
class PublicFormRateLimitFilterTests {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-31T10:00:00Z"));
    private final PublicFormRateLimitFilter filter = new PublicFormRateLimitFilter(
            new AuthProperties(5, Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(24),
                    Duration.ofHours(1), "http://localhost", "no-reply@findsport.by",
                    new AuthProperties.PublicForms(5, Duration.ofMinutes(1), Duration.ofHours(1))),
            clock, new ObjectMapper(), mock(SecurityAudit.class));

    @Test
    void sixthRequestWithinMinuteBlocksFormForAnHour() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(send("POST", "/api/v1/auth/login", "1.1.1.1")).isEqualTo(200);
        }
        assertThat(send("POST", "/api/v1/auth/login", "1.1.1.1")).isEqualTo(429);

        clock.advance(Duration.ofMinutes(59));
        assertThat(send("POST", "/api/v1/auth/login", "1.1.1.1")).isEqualTo(429);
        clock.advance(Duration.ofMinutes(1));
        assertThat(send("POST", "/api/v1/auth/login", "1.1.1.1")).isEqualTo(200);
    }

    @Test
    void counterRestartsInNextWindow() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(send("POST", "/api/v1/auth/register", "2.2.2.2")).isEqualTo(200);
        }
        clock.advance(Duration.ofSeconds(60));
        for (int i = 0; i < 5; i++) {
            assertThat(send("POST", "/api/v1/auth/register", "2.2.2.2")).isEqualTo(200);
        }
    }

    @Test
    void limitsAreSeparatePerClientAndFormAndSkipOtherRequests() throws Exception {
        for (int i = 0; i < 6; i++) {
            send("POST", "/api/v1/auth/login", "3.3.3.3");
        }
        assertThat(send("POST", "/api/v1/auth/login", "4.4.4.4")).isEqualTo(200);
        assertThat(send("POST", "/api/v1/auth/password/forgot", "3.3.3.3")).isEqualTo(200);
        for (int i = 0; i < 10; i++) {
            assertThat(send("GET", "/api/v1/events", "3.3.3.3")).isEqualTo(200);
            assertThat(send("POST", "/api/v1/provider/events", "3.3.3.3")).isEqualTo(200);
        }
    }

    private int send(String method, String uri, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

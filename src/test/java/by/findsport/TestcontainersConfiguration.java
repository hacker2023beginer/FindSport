package by.findsport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Тестовая инфраструктура: изолированный PostgreSQL той же версии, что в docker-compose.yml (ТЗ п. 6.7),
 * и почтовый сервис, перехватывающий письма. Контейнер переиспользуется всеми тестами с одинаковым контекстом Spring.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                .withEnv("TZ", "Europe/Minsk")
                .withEnv("PGTZ", "Europe/Minsk");
    }

    @Bean
    CapturingMailSender mailSender() {
        return new CapturingMailSender();
    }
}

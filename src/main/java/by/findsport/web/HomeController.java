package by.findsport.web;

import by.findsport.domain.Language;
import by.findsport.service.HomeService;
import by.findsport.web.dto.HomeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Главная страница Платформы (ТЗ п. 4.6.3): ближайшие мероприятия, быстрый выбор вида спорта
 * и региона. Доступна гостю (п. 4.2.1).
 */
@Tag(name = "Главная страница (публичная)", description = "Ближайшие мероприятия, виды спорта и регионы")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService home;

    @Operation(summary = "Данные главной страницы",
            description = "Ближайшие предстоящие мероприятия, виды спорта с числом мероприятий и регионы")
    @GetMapping
    public HomeResponse home(Locale locale) {
        return home.home(Language.from(locale));
    }
}

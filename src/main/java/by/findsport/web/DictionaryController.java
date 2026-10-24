package by.findsport.web;

import by.findsport.domain.Language;
import by.findsport.service.DictionaryService;
import by.findsport.web.dto.DictionaryItemResponse;
import by.findsport.web.mapper.ApiMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Справочники для быстрого выбора вида спорта и региона и фильтров (ТЗ п. 4.6.3, 4.7.2). */
@Tag(name = "Справочники", description = "Регионы и виды спорта для фильтров")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaries;

    @GetMapping("/regions")
    public List<DictionaryItemResponse> regions(Locale locale) {
        Language language = Language.from(locale);
        return dictionaries.regions().stream().map(r -> ApiMapper.region(r, language)).toList();
    }

    @GetMapping("/sports")
    public List<DictionaryItemResponse> sports(Locale locale) {
        Language language = Language.from(locale);
        return dictionaries.sports().stream().map(s -> ApiMapper.sport(s, language)).toList();
    }
}

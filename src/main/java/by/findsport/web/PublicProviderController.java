package by.findsport.web;

import by.findsport.domain.Language;
import by.findsport.service.ProviderProfileService;
import by.findsport.web.dto.PublicProviderResponse;
import by.findsport.web.mapper.ApiMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Публичные профили организаций и тренеров — только подтвержденные модератором (ТЗ п. 4.10.4–4.10.6). */
@Tag(name = "Организации и тренеры (публичные)", description = "Подтвержденные профили поставщиков, доступны гостю")
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class PublicProviderController {

    private final ProviderProfileService profiles;

    @Operation(summary = "Профиль организации или тренера",
            description = "Контакты передаются только в объеме, разрешенном владельцем")
    @GetMapping("/{id}")
    public PublicProviderResponse get(@PathVariable Long id, Locale locale) {
        return ApiMapper.publicProvider(profiles.getPublished(id), Language.from(locale));
    }
}

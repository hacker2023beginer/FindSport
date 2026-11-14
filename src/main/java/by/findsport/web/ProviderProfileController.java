package by.findsport.web;

import by.findsport.config.OpenApiConfig;
import by.findsport.domain.Language;
import by.findsport.service.ProviderProfileService;
import by.findsport.service.command.ProviderCommand;
import by.findsport.web.dto.LocalizedTextInput;
import by.findsport.web.dto.ProviderProfileRequest;
import by.findsport.web.dto.ProviderProfileResponse;
import by.findsport.web.mapper.ApiMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Профили организаций и тренеров в личном кабинете поставщика (ТЗ п. 4.6.7, 5.2)
 * и запрос на их подтверждение модератором (п. 4.10.5).
 */
@Tag(name = "Кабинет поставщика: профили", description = "Профили организаций и тренеров, подтверждение и архивирование")
@SecurityRequirement(name = OpenApiConfig.SESSION_AUTH)
@RestController
@RequestMapping("/api/v1/provider/profiles")
@RequiredArgsConstructor
public class ProviderProfileController {

    private final ProviderProfileService profiles;

    @GetMapping
    public List<ProviderProfileResponse> listOwn(Authentication auth, Locale locale) {
        Language language = Language.from(locale);
        return profiles.listOwn(auth.getName()).stream()
                .map(view -> ApiMapper.providerProfile(view, language))
                .toList();
    }

    @GetMapping("/{id}")
    public ProviderProfileResponse get(@PathVariable Long id, Authentication auth, Locale locale) {
        return ApiMapper.providerProfile(profiles.get(auth.getName(), id), Language.from(locale));
    }

    @Operation(summary = "Создать профиль в статусе «Черновик»")
    @PostMapping
    public ResponseEntity<ProviderProfileResponse> create(@Valid @RequestBody ProviderProfileRequest request,
                                                          Authentication auth, Locale locale) {
        ProviderProfileResponse created = ApiMapper.providerProfile(
                profiles.create(auth.getName(), toCommand(request)), Language.from(locale));
        return ResponseEntity.created(URI.create("/api/v1/provider/profiles/" + created.id())).body(created);
    }

    @Operation(summary = "Изменить профиль",
            description = "Существенные изменения опубликованного профиля (наименование, описание, место) "
                    + "уходят на модерацию, а публично до одобрения виден прежний профиль (поле revision)")
    @PutMapping("/{id}")
    public ProviderProfileResponse update(@PathVariable Long id, @Valid @RequestBody ProviderProfileRequest request,
                                          Authentication auth, Locale locale) {
        return ApiMapper.providerProfile(profiles.update(auth.getName(), id, toCommand(request)), Language.from(locale));
    }

    @Operation(summary = "Запросить подтверждение профиля: черновик или профиль, требующий доработки")
    @PostMapping("/{id}/submit")
    public ProviderProfileResponse submit(@PathVariable Long id, Authentication auth, Locale locale) {
        return ApiMapper.providerProfile(profiles.submitForModeration(auth.getName(), id), Language.from(locale));
    }

    @Operation(summary = "Архивировать опубликованный профиль")
    @PostMapping("/{id}/archive")
    public ProviderProfileResponse archive(@PathVariable Long id, Authentication auth, Locale locale) {
        return ApiMapper.providerProfile(profiles.archive(auth.getName(), id), Language.from(locale));
    }

    private static ProviderCommand toCommand(ProviderProfileRequest request) {
        return new ProviderCommand(
                request.type(),
                request.name().toDomain(),
                request.description().toDomain(),
                request.sportIds(),
                request.regionId(),
                request.address() == null ? null : request.address().toDomain(),
                LocalizedTextInput.toDomainOrNull(request.serviceArea()),
                request.workingHours().toDomain(),
                Inputs.text(request.contactPhone()),
                Inputs.email(request.contactEmail()),
                request.phonePublic(),
                request.emailPublic());
    }
}

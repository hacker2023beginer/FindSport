package by.findsport.web;

import by.findsport.service.ProviderProfileService;
import by.findsport.service.command.ProviderCommand;
import by.findsport.web.dto.LocalizedTextInput;
import by.findsport.web.dto.ProviderProfileRequest;
import by.findsport.web.dto.ProviderProfileResponse;
import by.findsport.web.mapper.ApiMapper;
import by.findsport.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Профили организаций и тренеров в личном кабинете поставщика (ТЗ п. 4.6.7, 5.2). */
@Tag(name = "Кабинет поставщика: профили", description = "Профили организаций и тренеров")
@SecurityRequirement(name = OpenApiConfig.BASIC_AUTH)
@RestController
@RequestMapping("/api/v1/provider/profiles")
@RequiredArgsConstructor
public class ProviderProfileController {

    private final ProviderProfileService profiles;

    @GetMapping
    public List<ProviderProfileResponse> listOwn(Authentication auth) {
        return profiles.listOwn(auth.getName()).stream().map(ApiMapper::providerProfile).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderProfileResponse create(@Valid @RequestBody ProviderProfileRequest request, Authentication auth) {
        var command = new ProviderCommand(
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
        return ApiMapper.providerProfile(profiles.create(auth.getName(), command));
    }
}

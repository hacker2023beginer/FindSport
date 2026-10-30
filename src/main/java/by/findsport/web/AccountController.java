package by.findsport.web;

import by.findsport.config.OpenApiConfig;
import by.findsport.domain.Language;
import by.findsport.service.UserProfileService;
import by.findsport.service.command.ProfileCommand;
import by.findsport.web.dto.MessageResponse;
import by.findsport.web.dto.PasswordChangeRequest;
import by.findsport.web.dto.ProfileRequest;
import by.findsport.web.dto.ProfileResponse;
import by.findsport.web.dto.SettingsRequest;
import by.findsport.web.mapper.ApiMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Личный кабинет: профиль, настройки и согласия, пароль, удаление (ТЗ п. 4.2.5, 4.6.6, 4.10.1, 4.10.2).
 * Идентификатора учетной записи в адресе нет — изменить чужие данные через этот API невозможно.
 */
@Tag(name = "Личный кабинет", description = "Профиль, настройки и согласия, смена пароля, удаление учетной записи")
@SecurityRequirement(name = OpenApiConfig.SESSION_AUTH)
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

    private final UserProfileService profiles;

    @GetMapping("/profile")
    public ProfileResponse profile(Authentication auth, Locale locale) {
        return ApiMapper.profile(profiles.profile(auth.getName()), Language.from(locale));
    }

    @PutMapping("/profile")
    public ProfileResponse updateProfile(@Valid @RequestBody ProfileRequest request, Authentication auth,
                                         Locale locale) {
        var command = new ProfileCommand(
                Inputs.text(request.displayName()),
                Inputs.text(request.phone()),
                request.regionId(),
                request.sportIds(),
                request.preferredLanguage());
        return ApiMapper.profile(profiles.updateProfile(auth.getName(), command), Language.from(locale));
    }

    @Operation(summary = "Согласие на персонализацию и e-mail-уведомления")
    @PutMapping("/settings")
    public ProfileResponse updateSettings(@RequestBody SettingsRequest request, Authentication auth, Locale locale) {
        return ApiMapper.profile(profiles.updateSettings(auth.getName(), request.personalizationConsent(),
                request.emailNotifications()), Language.from(locale));
    }

    @PutMapping("/password")
    public MessageResponse changePassword(@Valid @RequestBody PasswordChangeRequest request, Authentication auth) {
        profiles.changePassword(auth.getName(), request.currentPassword(), request.newPassword());
        return new MessageResponse("Пароль изменен");
    }

    @Operation(summary = "Запрос на удаление учетной записи: фиксируется, данные удаляются по регламенту")
    @DeleteMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProfileResponse requestDeletion(Authentication auth, Locale locale) {
        return ApiMapper.profile(profiles.requestDeletion(auth.getName()), Language.from(locale));
    }
}

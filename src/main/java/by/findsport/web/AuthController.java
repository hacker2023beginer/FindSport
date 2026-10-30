package by.findsport.web;

import by.findsport.config.OpenApiConfig;
import by.findsport.config.SessionAuthenticator;
import by.findsport.domain.Language;
import by.findsport.domain.User;
import by.findsport.service.LoginService;
import by.findsport.service.PasswordRecoveryService;
import by.findsport.service.RegistrationService;
import by.findsport.service.UserAccountService;
import by.findsport.service.command.RegistrationCommand;
import by.findsport.web.dto.CurrentUserResponse;
import by.findsport.web.dto.EmailRequest;
import by.findsport.web.dto.LoginRequest;
import by.findsport.web.dto.MessageResponse;
import by.findsport.web.dto.PasswordResetRequest;
import by.findsport.web.dto.RegisterRequest;
import by.findsport.web.dto.TokenRequest;
import by.findsport.web.mapper.ApiMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Регистрация, подтверждение e-mail, вход и восстановление доступа (ТЗ п. 4.2.2, 4.2.3).
 * Публичные формы ограничены по частоте (п. 4.2.14). Выход — {@code POST /api/v1/auth/logout}
 * (обрабатывается фильтром Spring Security, ответ 204).
 */
@Tag(name = "Авторизация", description = "Регистрация, подтверждение e-mail, вход, выход, восстановление доступа")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registration;
    private final LoginService login;
    private final PasswordRecoveryService recovery;
    private final UserAccountService accounts;
    private final SessionAuthenticator sessions;

    @Operation(summary = "Регистрация; на e-mail отправляется ссылка подтверждения")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request, Locale locale) {
        registration.register(new RegistrationCommand(
                Inputs.email(request.email()),
                request.password(),
                Inputs.text(request.displayName()),
                request.accountType(),
                Language.from(locale)));
        return new MessageResponse("Учетная запись создана. Мы отправили письмо со ссылкой для подтверждения "
                + "e-mail — перейдите по ней, чтобы войти");
    }

    @Operation(summary = "Подтверждение e-mail по токену из письма")
    @PostMapping("/email-verification")
    public MessageResponse verifyEmail(@Valid @RequestBody TokenRequest request) {
        registration.verifyEmail(request.token().trim());
        return new MessageResponse("E-mail подтвержден. Теперь можно войти");
    }

    @Operation(summary = "Повторная отправка письма подтверждения")
    @PostMapping("/email-verification/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest request) {
        registration.resendVerification(Inputs.email(request.email()));
        return new MessageResponse("Если учетная запись ожидает подтверждения, мы отправили новое письмо");
    }

    @Operation(summary = "Вход: открывает сессию (cookie JSESSIONID) и выдает новый CSRF-токен")
    @PostMapping("/login")
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = login.authenticate(Inputs.email(request.email()), request.password());
        sessions.signIn(user.getEmail(), user.getRole(), httpRequest, httpResponse);
        return ApiMapper.currentUser(user);
    }

    @Operation(summary = "Текущий пользователь сессии")
    @SecurityRequirement(name = OpenApiConfig.SESSION_AUTH)
    @GetMapping("/me")
    public CurrentUserResponse me(Authentication auth) {
        return ApiMapper.currentUser(accounts.requireByEmail(auth.getName()));
    }

    @Operation(summary = "Запрос восстановления доступа: ссылка на смену пароля приходит на e-mail")
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request) {
        recovery.requestReset(Inputs.email(request.email()));
        return new MessageResponse("Если учетная запись с таким e-mail существует, мы отправили письмо "
                + "со ссылкой для восстановления доступа");
    }

    @Operation(summary = "Новый пароль по ссылке восстановления")
    @PostMapping("/password/reset")
    public MessageResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        recovery.resetPassword(request.token().trim(), request.newPassword());
        return new MessageResponse("Пароль изменен. Войдите с новым паролем");
    }
}

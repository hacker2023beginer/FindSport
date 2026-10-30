package by.findsport.web;

import by.findsport.config.OpenApiConfig;
import by.findsport.service.UserAdministrationService;
import by.findsport.web.dto.AdminUserResponse;
import by.findsport.web.dto.PageResponse;
import by.findsport.web.dto.RoleChangeRequest;
import by.findsport.web.mapper.ApiMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Учетные записи и роли (ТЗ п. 4.5.1, 4.5.2). Доступ: только администратор (Приложение 2). */
@Tag(name = "Администрирование: пользователи", description = "Реестр учетных записей, роли, блокировка")
@SecurityRequirement(name = OpenApiConfig.SESSION_AUTH)
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdministrationService administration;

    @GetMapping
    public PageResponse<AdminUserResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(administration.list(page, size), ApiMapper::adminUser);
    }

    @PutMapping("/{id}/role")
    public AdminUserResponse changeRole(@PathVariable Long id, @Valid @RequestBody RoleChangeRequest request,
                                        Authentication auth) {
        return ApiMapper.adminUser(administration.changeRole(auth.getName(), id, request.role()));
    }

    @PostMapping("/{id}/block")
    public AdminUserResponse block(@PathVariable Long id, Authentication auth) {
        return ApiMapper.adminUser(administration.setBlocked(auth.getName(), id, true));
    }

    @PostMapping("/{id}/unblock")
    public AdminUserResponse unblock(@PathVariable Long id, Authentication auth) {
        return ApiMapper.adminUser(administration.setBlocked(auth.getName(), id, false));
    }
}

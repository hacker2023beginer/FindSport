package by.findsport.web.dto;

import by.findsport.domain.UserRole;

/** Текущая сессия: кто вошел и с какой ролью — интерфейс строит по ней меню (ТЗ Приложение 2). */
public record CurrentUserResponse(Long id, String email, UserRole role, String displayName) {
}

package by.findsport.service.command;

import by.findsport.domain.Language;
import java.util.Set;

/** Редактируемые данные профиля пользователя (ТЗ п. 4.10.1). E-mail — логин и здесь не меняется. */
public record ProfileCommand(
        String displayName,
        String phone,
        Long regionId,
        Set<Long> sportIds,
        Language preferredLanguage) {
}

package by.findsport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Текст на русском и английском языках (ТЗ п. 4.1.6). Колонки задаются через
 * {@code @AttributeOverride} в сущности: {@code <поле>_ru}, {@code <поле>_en}.
 * Русская версия обязательна; при отсутствии английской отображается русская
 * (политика отображения по п. 5.8 — подлежит согласованию с Заказчиком).
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LocalizedText {

    @Column(name = "ru")
    private String ru;

    @Column(name = "en")
    private String en;

    public String get(Language language) {
        return language == Language.EN && en != null && !en.isBlank() ? en : ru;
    }
}

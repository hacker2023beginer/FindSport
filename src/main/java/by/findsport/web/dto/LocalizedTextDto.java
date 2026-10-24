package by.findsport.web.dto;

import by.findsport.domain.LocalizedText;

/** Обе языковые версии текста — для кабинета поставщика и модератора. */
public record LocalizedTextDto(String ru, String en) {

    public static LocalizedTextDto of(LocalizedText text) {
        return text == null ? null : new LocalizedTextDto(text.getRu(), text.getEn());
    }
}

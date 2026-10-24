package by.findsport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Адрес в формате ТЗ п. 7.6: район (при наличии), населенный пункт, улица, номер здания.
 * Область хранится отдельной ссылкой на справочник {@link Region} в самой сущности,
 * так как по ней выполняется фильтрация (п. 4.7.2).
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Address {

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "locality", length = 100)
    private String locality;

    @Column(name = "street", length = 150)
    private String street;

    @Column(name = "building", length = 20)
    private String building;
}

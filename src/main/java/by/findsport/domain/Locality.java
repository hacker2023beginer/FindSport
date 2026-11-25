package by.findsport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Населенный пункт с координатами — источник меток карты (ТЗ п. 4.6.2, 4.7.2).
 * <p>
 * Адрес карточки хранится по ТЗ п. 7.6 (область, район, населенный пункт, улица, дом)
 * и координат в нем не содержит, поэтому положение метки определяется по населенному пункту.
 */
@Getter
@Setter
@Entity
@Table(name = "locality")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Locality extends BaseEntity {

    @Column(name = "name_ru", nullable = false, length = 100)
    private String nameRu;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    /** Код области из справочника регионов (п. 4.5.3). */
    @Column(name = "region_code", nullable = false, length = 50)
    private String regionCode;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;
}

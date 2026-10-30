package by.findsport.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Справочник регионов (областей и г. Минска), ТЗ п. 4.5.3, 7.6.
 */
@Getter
@Setter
@Entity
@Table(name = "region")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ru", column = @Column(name = "name_ru", nullable = false, length = 200)),
            @AttributeOverride(name = "en", column = @Column(name = "name_en", length = 200))
    })
    private LocalizedText name;

    public Region(String code, LocalizedText name) {
        this.code = code;
        this.name = name;
    }
}

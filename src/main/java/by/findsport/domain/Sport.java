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
 * Справочник видов спорта, управляемый администратором (ТЗ п. 4.5.3).
 */
@Getter
@Setter
@Entity
@Table(name = "sport")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sport extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ru", column = @Column(name = "name_ru", nullable = false, length = 200)),
            @AttributeOverride(name = "en", column = @Column(name = "name_en", length = 200))
    })
    private LocalizedText name;

    public Sport(String code, LocalizedText name) {
        this.code = code;
        this.name = name;
    }
}

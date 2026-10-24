package by.findsport.web.validation;

import by.findsport.config.FileUploadProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/** Экземпляр создается Spring-фабрикой валидаторов, поэтому пределы внедряются из конфигурации. */
public class AllowedFileValidator implements ConstraintValidator<AllowedFile, MultipartFile> {

    private final FileUploadProperties limits;
    private FileKind kind;

    public AllowedFileValidator(FileUploadProperties limits) {
        this.limits = limits;
    }

    @Override
    public void initialize(AllowedFile annotation) {
        this.kind = annotation.value();
    }

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        if (file == null || file.isEmpty()) {
            return reject(context, "Файл не передан");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (kind.extensions().stream().noneMatch(name::endsWith)) {
            return reject(context, "Допустимые расширения: " + String.join(", ", kind.extensions()));
        }
        if (file.getContentType() == null || !kind.contentTypes().contains(file.getContentType().toLowerCase(Locale.ROOT))) {
            return reject(context, "Недопустимый тип содержимого файла");
        }
        DataSize min = kind == FileKind.IMAGE ? limits.imageMinSize() : DataSize.ofBytes(1);
        DataSize max = kind == FileKind.IMAGE ? limits.imageMaxSize() : limits.documentMaxSize();
        if (file.getSize() < min.toBytes() || file.getSize() > max.toBytes()) {
            return reject(context, "Размер файла должен быть от %d КБ до %d МБ"
                    .formatted(min.toKilobytes(), max.toMegabytes()));
        }
        if (!hasSignature(file)) {
            return reject(context, "Содержимое файла не соответствует его формату");
        }
        return true;
    }

    private boolean hasSignature(MultipartFile file) {
        byte[] expected = kind.signature();
        try (InputStream in = file.getInputStream()) {
            return Arrays.equals(in.readNBytes(expected.length), expected);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}

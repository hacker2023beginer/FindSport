package by.findsport.service.storage;

import by.findsport.config.FileUploadProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Файловое хранилище загруженных материалов (ТЗ п. 4.13.7): каталог вне исполняемых каталогов
 * и статики приложения, имя файла генерируется сервером — исходное имя клиента в путь не попадает.
 * <p>
 * Файлы согласованы с транзакцией БД: записанный файл удаляется при откате, а удаление
 * файла выполняется только после фиксации транзакции.
 */
@Slf4j
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(FileUploadProperties properties) {
        this.root = properties.storageDir().toAbsolutePath().normalize();
    }

    /** Сохраняет содержимое и возвращает ключ хранилища вида {@code <directory>/<uuid><extension>}. */
    public String store(String directory, String extension, InputStreamSource content) {
        String key = directory + "/" + UUID.randomUUID() + extension;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try (InputStream in = content.getInputStream()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сохранить файл", e);
        }
        onRollback(() -> deleteQuietly(key));
        return key;
    }

    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            throw new UncheckedIOException(new IOException("Файл отсутствует в хранилище: " + key));
        }
        return new PathResource(path);
    }

    /** Удаляет файл после успешной фиксации транзакции, удалившей запись о нем. */
    public void deleteAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(key);
            }
        });
    }

    private void onRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    action.run();
                }
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Не удалось удалить файл из хранилища: {}", key, e);
        }
    }

    /** Ключ формирует только сервер, но выход за пределы хранилища дополнительно запрещен. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Недопустимый ключ хранилища");
        }
        return path;
    }
}

package by.findsport;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/** Настоящие файлы для проверок загрузки: валидатор читает JPEG декодером и DOCX как ZIP-контейнер. */
public final class TestFiles {

    private TestFiles() {
    }

    /** JPEG из шума (плохо сжимается) размером не меньше {@code minBytes}; детерминирован. */
    public static byte[] jpeg(int minBytes) {
        int side = 256;
        while (true) {
            byte[] bytes = jpegOfSide(side);
            if (bytes.length >= minBytes) {
                return bytes;
            }
            side += 128;
        }
    }

    /** Минимальный DOCX: описание типов содержимого и основная часть документа. */
    public static byte[] docx() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] jpegOfSide(int side) {
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpg", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}

package app.snatter.server.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageInfoTest {

    static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height,
            format.equals("jpg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, format, out), "no writer for " + format);
        return out.toByteArray();
    }

    @Test
    void detectsPng() throws IOException {
        assertEquals(new ImageInfo("image/png", 64, 48), ImageInfo.detect(image("png", 64, 48)).orElseThrow());
    }

    @Test
    void detectsJpeg() throws IOException {
        assertEquals(new ImageInfo("image/jpeg", 100, 40), ImageInfo.detect(image("jpg", 100, 40)).orElseThrow());
    }

    @Test
    void detectsGif() throws IOException {
        assertEquals(new ImageInfo("image/gif", 32, 32), ImageInfo.detect(image("gif", 32, 32)).orElseThrow());
    }

    @Test
    void detectsWebpExtendedHeader() {
        byte[] b = webpHeader("VP8X", new byte[10]);
        // canvas width - 1 = 255 (0x0000FF), height - 1 = 599 (0x000257), little endian at 24 and 27
        b[24] = (byte) 0xFF; b[25] = 0; b[26] = 0;
        b[27] = (byte) 0x57; b[28] = 0x02; b[29] = 0;
        assertEquals(new ImageInfo("image/webp", 256, 600), ImageInfo.detect(b).orElseThrow());
    }

    @Test
    void detectsWebpLossyHeader() {
        byte[] b = webpHeader("VP8 ", new byte[10]);
        // frame tag and start code occupy 20..25; width at 26, height at 28 (14 bits each)
        b[26] = (byte) 0x90; b[27] = 0x01;   // 400
        b[28] = 0x2C; b[29] = 0x01;          // 300
        assertEquals(new ImageInfo("image/webp", 400, 300), ImageInfo.detect(b).orElseThrow());
    }

    @Test
    void detectsWebpLosslessHeader() {
        byte[] b = webpHeader("VP8L", new byte[10]);
        b[20] = 0x2F;                         // signature
        // width - 1 = 511 (14 bits), height - 1 = 255 (14 bits)
        b[21] = (byte) 0xFF; b[22] = (byte) 0x01 | (byte) 0xC0; b[23] = 0x3F; b[24] = 0x00;
        assertEquals(new ImageInfo("image/webp", 512, 256), ImageInfo.detect(b).orElseThrow());
    }

    @Test
    void rejectsNonImages() {
        assertEquals(Optional.empty(), ImageInfo.detect("hello world, definitely not an image".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Optional.empty(), ImageInfo.detect(new byte[0]));
        assertEquals(Optional.empty(), ImageInfo.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, 0, 0}));
        assertEquals(Optional.empty(), ImageInfo.detect("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] webpHeader(String chunk, byte[] payload) {
        byte[] b = new byte[30];
        put(b, 0, "RIFF");
        put(b, 8, "WEBP");
        put(b, 12, chunk);
        return b;
    }

    private static void put(byte[] b, int at, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, b, at, bytes.length);
    }
}

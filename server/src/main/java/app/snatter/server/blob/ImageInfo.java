package app.snatter.server.blob;

import java.util.Optional;

/**
 * Detects the format and pixel dimensions of an image by reading its header.
 * Supports PNG, JPEG, GIF and WebP. The declared Content-Type of an upload is
 * ignored in favour of what the bytes actually are.
 */
public record ImageInfo(String contentType, int width, int height) {

    public static Optional<ImageInfo> detect(byte[] b) {
        if (isPng(b)) {
            return Optional.of(new ImageInfo("image/png", be32(b, 16), be32(b, 20)));
        }
        if (isGif(b)) {
            return Optional.of(new ImageInfo("image/gif", le16(b, 6), le16(b, 8)));
        }
        if (isJpeg(b)) {
            return jpegDimensions(b);
        }
        if (isWebp(b)) {
            return webpDimensions(b);
        }
        return Optional.empty();
    }

    private static boolean isPng(byte[] b) {
        return b.length >= 24
            && u(b, 0) == 0x89 && u(b, 1) == 'P' && u(b, 2) == 'N' && u(b, 3) == 'G'
            && u(b, 4) == 0x0D && u(b, 5) == 0x0A && u(b, 6) == 0x1A && u(b, 7) == 0x0A
            && u(b, 12) == 'I' && u(b, 13) == 'H' && u(b, 14) == 'D' && u(b, 15) == 'R';
    }

    private static boolean isGif(byte[] b) {
        return b.length >= 10
            && u(b, 0) == 'G' && u(b, 1) == 'I' && u(b, 2) == 'F' && u(b, 3) == '8'
            && (u(b, 4) == '7' || u(b, 4) == '9') && u(b, 5) == 'a';
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 4 && u(b, 0) == 0xFF && u(b, 1) == 0xD8;
    }

    private static boolean isWebp(byte[] b) {
        return b.length >= 30
            && u(b, 0) == 'R' && u(b, 1) == 'I' && u(b, 2) == 'F' && u(b, 3) == 'F'
            && u(b, 8) == 'W' && u(b, 9) == 'E' && u(b, 10) == 'B' && u(b, 11) == 'P';
    }

    /** Walks JPEG segments until the first start-of-frame marker, which carries the dimensions. */
    private static Optional<ImageInfo> jpegDimensions(byte[] b) {
        int i = 2;
        while (i + 9 < b.length) {
            if (u(b, i) != 0xFF) {
                return Optional.empty();
            }
            int marker = u(b, i + 1);
            if (marker == 0xFF) {            // padding
                i++;
                continue;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) { // standalone markers
                i += 2;
                continue;
            }
            int length = be16(b, i + 2);
            if (isStartOfFrame(marker)) {
                return Optional.of(new ImageInfo("image/jpeg", be16(b, i + 7), be16(b, i + 5)));
            }
            if (length < 2) {
                return Optional.empty();
            }
            i += 2 + length;
        }
        return Optional.empty();
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    private static Optional<ImageInfo> webpDimensions(byte[] b) {
        String chunk = "" + (char) u(b, 12) + (char) u(b, 13) + (char) u(b, 14) + (char) u(b, 15);
        return switch (chunk) {
            case "VP8 " -> Optional.of(new ImageInfo("image/webp", le16(b, 26) & 0x3FFF, le16(b, 28) & 0x3FFF));
            case "VP8L" -> {
                int w = 1 + (u(b, 21) | ((u(b, 22) & 0x3F) << 8));
                int h = 1 + (((u(b, 22) >> 6) & 0x03) | (u(b, 23) << 2) | ((u(b, 24) & 0x0F) << 10));
                yield Optional.of(new ImageInfo("image/webp", w, h));
            }
            case "VP8X" -> Optional.of(new ImageInfo("image/webp", 1 + le24(b, 24), 1 + le24(b, 27)));
            default -> Optional.empty();
        };
    }

    private static int u(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static int be16(byte[] b, int i) {
        return (u(b, i) << 8) | u(b, i + 1);
    }

    private static int be32(byte[] b, int i) {
        return (be16(b, i) << 16) | be16(b, i + 2);
    }

    private static int le16(byte[] b, int i) {
        return u(b, i) | (u(b, i + 1) << 8);
    }

    private static int le24(byte[] b, int i) {
        return le16(b, i) | (u(b, i + 2) << 16);
    }
}

package app.snatter.server.account;

import app.snatter.server.api.ApiException;
import app.snatter.server.blob.Blob;
import app.snatter.server.blob.BlobService;
import app.snatter.server.blob.ImageInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Profile pictures. The server validates and stores the image as uploaded; it
 * does not resize, so clients should crop and scale before uploading.
 */
@ApplicationScoped
public class AvatarService {

    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_DIMENSION = 1024;
    public static final int MIN_DIMENSION = 32;
    static final String PURPOSE = "avatar";

    private final AccountRepository accounts;
    private final BlobService blobs;

    public AvatarService(AccountRepository accounts, BlobService blobs) {
        this.accounts = accounts;
        this.blobs = blobs;
    }

    @Transactional
    public Account set(AccountId accountId, byte[] image) {
        if (image.length > MAX_BYTES) {
            throw new ApiException(413, "image_too_large", "Avatar must be at most " + MAX_BYTES + " bytes");
        }
        ImageInfo info = ImageInfo.detect(image)
            .orElseThrow(() -> ApiException.badRequest("unsupported_image", "Avatar must be a PNG, JPEG, GIF or WebP image"));
        if (info.width() > MAX_DIMENSION || info.height() > MAX_DIMENSION) {
            throw ApiException.badRequest("image_dimensions", "Avatar must be at most " + MAX_DIMENSION + "x" + MAX_DIMENSION + " pixels");
        }
        if (info.width() < MIN_DIMENSION || info.height() < MIN_DIMENSION) {
            throw ApiException.badRequest("image_dimensions", "Avatar must be at least " + MIN_DIMENSION + "x" + MIN_DIMENSION + " pixels");
        }

        Account current = accounts.findById(accountId)
            .orElseThrow(() -> ApiException.notFound("account_not_found", "No such account"));
        Blob blob = blobs.store(image, info.contentType(), accountId, PURPOSE);
        accounts.setAvatar(accountId, blob.id());
        if (current.avatarId() != null) {
            blobs.delete(current.avatarId());
        }
        return accounts.findById(accountId).orElseThrow();
    }

    @Transactional
    public Account clear(AccountId accountId) {
        Account current = accounts.findById(accountId)
            .orElseThrow(() -> ApiException.notFound("account_not_found", "No such account"));
        if (current.avatarId() != null) {
            accounts.setAvatar(accountId, null);
            blobs.delete(current.avatarId());
        }
        return accounts.findById(accountId).orElseThrow();
    }
}

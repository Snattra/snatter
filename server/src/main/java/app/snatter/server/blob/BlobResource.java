package app.snatter.server.blob;

import app.snatter.api.BlobsApi;
import app.snatter.server.api.ApiException;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;

/**
 * Serves blob bytes. Blobs are immutable and their ids unguessable, so this
 * endpoint is public and tells clients to cache forever. That also lets
 * {@code <img>} tags load avatars without an Authorization header.
 */
public class BlobResource implements BlobsApi {

    private final BlobService blobs;

    public BlobResource(BlobService blobs) {
        this.blobs = blobs;
    }

    @Override
    public Response getBlob(BlobId id) {
        Blob blob = blobs.find(id)
            .orElseThrow(() -> ApiException.notFound("blob_not_found", "No such blob"));
        InputStream content = blobs.open(id)
            .orElseThrow(() -> ApiException.notFound("blob_not_found", "No such blob"));
        return Response.ok(content, blob.contentType())
            .header("Content-Length", blob.sizeBytes())
            .header("Cache-Control", "public, max-age=31536000, immutable")
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", "inline")
            .build();
    }
}

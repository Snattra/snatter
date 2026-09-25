package app.snatter.server.moderation;

import app.snatter.api.model.ApiErrorDto;
import app.snatter.api.model.BanNoticeDto;
import app.snatter.server.api.ApiException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** A banned member tried to log in; answered with the reason in {@code ApiError.ban}. */
public class BannedException extends ApiException {

    private final Ban ban;

    public BannedException(Ban ban) {
        super(403, "banned", "You are banned from this server");
        this.ban = ban;
    }

    public static class Mapper {

        @ServerExceptionMapper
        public Response banned(BannedException e) {
            return Response.status(e.status())
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiErrorDto().error(e.code()).message(e.getMessage())
                    .ban(new BanNoticeDto().reason(e.ban.reason()).bannedAt(e.ban.createdAt())))
                .build();
        }
    }
}

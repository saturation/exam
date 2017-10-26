package sanitizers;

import com.fasterxml.jackson.databind.JsonNode;
import play.mvc.Http;
import play.mvc.Result;

import java.util.concurrent.CompletionStage;

public class CommentSanitizer extends play.mvc.Action.Simple {

    public CompletionStage<Result> call(Http.Context ctx) {
        JsonNode body = ctx.request().body().asJson();
        return delegate.call(ctx.withRequest(sanitize(ctx, body)));
    }

    private Http.Request sanitize(Http.Context ctx, JsonNode body) {
        return SanitizingHelper.sanitizeOptional("comment", body, String.class, Attrs.COMMENT, ctx.request());
    }
}

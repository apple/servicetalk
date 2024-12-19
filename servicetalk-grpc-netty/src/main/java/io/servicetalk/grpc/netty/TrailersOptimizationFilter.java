package io.servicetalk.grpc.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;

import static io.servicetalk.http.api.HttpContextKeys.HTTP_OPTIMIZE_ERROR_STREAM;

public class TrailersOptimizationFilter extends StreamingHttpServiceFilter {
    public TrailersOptimizationFilter(StreamingHttpService delegate) {
        super(delegate);
    }

    @Override
    public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request, StreamingHttpResponseFactory responseFactory) {
        return super.handle(ctx, request, responseFactory).flatMap(response -> {
            Single<StreamingHttpResponse> mappedResponse;
            if (Boolean.TRUE.equals(response.context().get(HTTP_OPTIMIZE_ERROR_STREAM))) {
                mappedResponse = response.toResponse().map(HttpResponse::toStreamingResponse);
            } else {
                mappedResponse = Single.succeeded(response);
            }
            return mappedResponse.shareContextOnSubscribe();
        });
    }
}

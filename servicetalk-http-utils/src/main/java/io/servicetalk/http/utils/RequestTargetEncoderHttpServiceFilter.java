/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import java.nio.charset.Charset;

import static io.servicetalk.concurrent.api.Single.defer;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

/**
 * <a href="https://tools.ietf.org/html/rfc3986#section-2.1">Encodes</a> the
 * {@link StreamingHttpRequest#requestTarget()} for each incoming request.
 * <p>
 * URI producers <a href="https://tools.ietf.org/html/rfc3986">should encode query strings</a>, but
 * just in case they don't this filter will do the encoding.
 */
public final class RequestTargetEncoderHttpServiceFilter implements StreamingHttpServiceFilterFactory,
                                                                    HttpExecutionStrategyInfluencer {
    private final Charset charset;

    /**
     * Create a new instance.
     */
    public RequestTargetEncoderHttpServiceFilter() {
        this(US_ASCII);
    }

    /**
     * Create a new instance.
     * @param charset The charset to use for the encoding.
     */
    public RequestTargetEncoderHttpServiceFilter(Charset charset) {
        this.charset = requireNonNull(charset);
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return defer(() -> {
                    request.requestTarget(request.requestTarget(), charset);
                    return delegate().handle(ctx, request, responseFactory).subscribeShareContext();
                });
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence since we do not block.
        return strategy;
    }
}

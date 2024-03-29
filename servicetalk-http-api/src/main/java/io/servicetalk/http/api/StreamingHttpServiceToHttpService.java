/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import static java.util.Objects.requireNonNull;

final class StreamingHttpServiceToHttpService implements HttpService {
    private final StreamingHttpService original;

    StreamingHttpServiceToHttpService(final StreamingHttpService original) {
        this.original = requireNonNull(original);
    }

    @Override
    public Single<HttpResponse> handle(final HttpServiceContext ctx,
                                       final HttpRequest request,
                                       final HttpResponseFactory responseFactory) {
        return original.handle(ctx, request.toStreamingRequest(), ctx.streamingResponseFactory())
                .flatMap(resp -> resp.toResponse().shareContextOnSubscribe());
    }

    @Override
    public Completable closeAsync() {
        return original.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return original.closeAsyncGracefully();
    }
}

/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static java.util.Objects.requireNonNull;

final class StreamingHttpServiceToBlockingHttpService extends BlockingHttpService {
    private final StreamingHttpService service;

    StreamingHttpServiceToBlockingHttpService(StreamingHttpService service) {
        this.service = requireNonNull(service);
    }

    @Override
    public HttpResponse handle(final HttpServiceContext ctx, final HttpRequest request,
                               final HttpResponseFactory responseFactory) throws Exception {
        return blockingInvocation(service.handle(ctx, request.toStreamingRequest(), ctx.streamingResponseFactory())
                .flatMap(StreamingHttpResponse::toResponse));
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(service.closeAsync());
    }

    @Override
    StreamingHttpService asStreamingServiceInternal() {
        return service;
    }
}

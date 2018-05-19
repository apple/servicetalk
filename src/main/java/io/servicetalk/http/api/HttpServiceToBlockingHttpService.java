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

import io.servicetalk.transport.api.ConnectionContext;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpRequests.fromBlockingRequest;
import static java.util.Objects.requireNonNull;

final class HttpServiceToBlockingHttpService extends BlockingHttpService {
    private final HttpService service;

    HttpServiceToBlockingHttpService(HttpService service) {
        this.service = requireNonNull(service);
    }

    @Override
    public BlockingHttpResponse<HttpPayloadChunk> handle(final ConnectionContext ctx,
                                                         final BlockingHttpRequest<HttpPayloadChunk> request)
            throws Exception {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        return new DefaultBlockingHttpResponse<>(
                awaitIndefinitelyNonNull(service.handle(ctx, fromBlockingRequest(request))));
    }

    @Override
    public void close() throws Exception {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        awaitIndefinitely(service.closeAsync());
    }

    @Override
    HttpService asAsynchronousServiceInternal() {
        return service;
    }
}

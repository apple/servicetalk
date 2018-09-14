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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static java.util.Objects.requireNonNull;

final class BlockingHttpServiceToStreamingHttpService extends StreamingHttpService {
    private final BlockingHttpService service;

    BlockingHttpServiceToStreamingHttpService(BlockingHttpService service) {
        this.service = requireNonNull(service);
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory factory) {
        return request.toRequest().flatMap(req -> blockingToSingle(() -> service.handle(
                ctx, req, ctx.getResponseFactory())) .map(HttpResponse::toStreamingResponse));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(service::close);
    }

    @Override
    BlockingHttpService asBlockingServiceInternal() {
        return service;
    }
}

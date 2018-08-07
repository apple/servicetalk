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
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static java.util.Objects.requireNonNull;

final class BlockingHttpRequesterToHttpRequester extends HttpRequester {
    private final BlockingHttpRequester blockingHttpRequester;

    BlockingHttpRequesterToHttpRequester(BlockingHttpRequester blockingHttpRequester) {
        this.blockingHttpRequester = requireNonNull(blockingHttpRequester);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
        return BlockingUtils.request(blockingHttpRequester, request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return blockingHttpRequester.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        if (blockingHttpRequester instanceof HttpRequesterToBlockingHttpRequester) {
            return ((HttpRequesterToBlockingHttpRequester) blockingHttpRequester).onClose();
        }

        return error(new UnsupportedOperationException("unsupported type: " + blockingHttpRequester.getClass()));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(blockingHttpRequester::close);
    }

    @Override
    BlockingHttpRequester asBlockingRequesterInternal() {
        return blockingHttpRequester;
    }
}

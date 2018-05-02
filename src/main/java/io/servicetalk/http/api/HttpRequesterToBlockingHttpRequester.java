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
import io.servicetalk.transport.api.ExecutionContext;

import static java.util.Objects.requireNonNull;

final class HttpRequesterToBlockingHttpRequester<I, O> extends BlockingHttpRequester<I, O> {
    private final HttpRequester<I, O> requester;

    HttpRequesterToBlockingHttpRequester(HttpRequester<I, O> requester) {
        this.requester = requireNonNull(requester);
    }

    @Override
    public BlockingHttpResponse<O> request(final BlockingHttpRequest<I> request) throws Exception {
        return BlockingUtils.request(requester, request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return requester.getExecutionContext();
    }

    @Override
    public void close() throws Exception {
        BlockingUtils.close(requester);
    }

    Completable onClose() {
        return requester.onClose();
    }

    @Override
    HttpRequester<I, O> asAsynchronousRequesterInternal() {
        return requester;
    }
}

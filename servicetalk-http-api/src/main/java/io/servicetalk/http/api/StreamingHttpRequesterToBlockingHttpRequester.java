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

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static java.util.Objects.requireNonNull;

final class StreamingHttpRequesterToBlockingHttpRequester extends BlockingHttpRequester {
    private final StreamingHttpRequester requester;

    StreamingHttpRequesterToBlockingHttpRequester(StreamingHttpRequester requester) {
        super(new StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory(requester.reqRespFactory));
        this.requester = requireNonNull(requester);
    }

    @Override
    public HttpResponse request(final HttpExecutionStrategy strategy, final HttpRequest request) throws Exception {
        return blockingInvocation(requester.request(strategy, request.toStreamingRequest())
                .flatMap(StreamingHttpResponse::toResponse));
    }

    @Override
    public ExecutionContext executionContext() {
        return requester.executionContext();
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(requester.closeAsync());
    }

    @Override
    StreamingHttpRequester asStreamingRequesterInternal() {
        return requester;
    }

    Completable onClose() {
        return requester.onClose();
    }
}

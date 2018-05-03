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
package io.servicetalk.examples.http.helloworld;

import io.servicetalk.http.api.BlockingHttpRequest;
import io.servicetalk.http.api.BlockingHttpResponse;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.transport.api.ConnectionContext;

import static io.servicetalk.http.api.BlockingHttpResponses.newResponse;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;

/**
 * Business logic for the Hello World application.
 * This is a blocking service, for asynchronous use {@link HelloWorldService}.
 */
final class HelloWorldBlockingService extends BlockingHttpService<HttpPayloadChunk, HttpPayloadChunk> {

    @Override
    public BlockingHttpResponse<HttpPayloadChunk> handle(final ConnectionContext ctx, final BlockingHttpRequest<HttpPayloadChunk> request) {
        return newResponse(OK, newPayloadChunk(ctx.getBufferAllocator().fromAscii("Hello World!")), ctx.getExecutor());
    }
}

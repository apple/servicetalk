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
package io.servicetalk.examples.http.helloworld.blocking.streaming;

import io.servicetalk.examples.http.helloworld.async.streaming.HelloWorldStreamingService;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.transport.api.ConnectionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.http.api.BlockingStreamingHttpResponses.newResponse;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;

/**
 * Business logic for the Hello World application.
 * This is a blocking service, for asynchronous use {@link HelloWorldStreamingService}.
 */
final class HelloWorldBlockingStreamingService extends BlockingStreamingHttpService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HelloWorldBlockingStreamingService.class);

    @Override
    public BlockingStreamingHttpResponse<HttpPayloadChunk> handle(final ConnectionContext ctx,
                                                                  final BlockingStreamingHttpRequest<HttpPayloadChunk> request) {
        // Log the request meta data and headers, by default the header values will be filtered for
        // security reasons, however here we override the filter and print every value.
        LOGGER.info("got request {}", request.toString((name, value) -> value));

        return newResponse(OK, ctx.getExecutionContext().getBufferAllocator().fromAscii("Hello World!"));
    }
}

/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.reactivestreams.tck.AbstractSingleTckTest;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponses;

import java.time.Duration;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;

public class JavaNetSoTimeoutHttpConnectionFilterTckTest extends AbstractSingleTckTest<StreamingHttpResponse> {

    @Override
    protected Publisher<StreamingHttpResponse> createServiceTalkPublisher(long elements) {
        StreamingHttpResponse response = StreamingHttpResponses.newResponse(OK, HTTP_1_1,
                        DefaultHttpHeadersFactory.INSTANCE.newHeaders(), DEFAULT_ALLOCATOR,
                        DefaultHttpHeadersFactory.INSTANCE);
        return Single.succeeded(response).<StreamingHttpResponse>liftSync(subscriber ->
                new JavaNetSoTimeoutHttpConnectionFilter.RequestTimeoutSubscriber(subscriber, Completable.completed(),
                        Duration.ofMillis(1000), Executors.global())
        ).toPublisher();
    }
}

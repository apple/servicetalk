/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.reactivestreams.tck.AbstractPublisherOperatorTckTest;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponses;

import org.testng.annotations.Test;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;

@Test
public class BeforeFinallyHttpOperatorTckTest extends AbstractPublisherOperatorTckTest<Buffer> {

    @Override
    protected Publisher<Buffer> composePublisher(final Publisher<Integer> publisher, final int elements) {
        return Single.succeeded(StreamingHttpResponses.newResponse(OK, HTTP_1_1,
                                DefaultHttpHeadersFactory.INSTANCE.newHeaders(), DEFAULT_ALLOCATOR,
                                DefaultHttpHeadersFactory.INSTANCE)
                        .payloadBody(publisher.map(Integer::toHexString).map(DEFAULT_ALLOCATOR::fromAscii)))
                .liftSync(new BeforeFinallyHttpOperator(() -> { /* noop */ }))
                .flatMapPublisher(StreamingHttpResponse::payloadBody);
    }

    @Override
    public void required_spec313_cancelMustMakeThePublisherEventuallyDropAllReferencesToTheSubscriber() {
        // FIXME: unskip this test
        notVerified();
    }
}

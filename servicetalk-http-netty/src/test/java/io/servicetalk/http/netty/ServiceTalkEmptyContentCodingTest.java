/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static io.servicetalk.encoding.api.ContentCodings.identity;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;

@RunWith(Parameterized.class)
public class ServiceTalkEmptyContentCodingTest extends ServiceTalkContentCodingTest {

    public ServiceTalkEmptyContentCodingTest(final HttpProtocol protocol, final Codings serverCodings,
                                             final Codings clientCodings, final Compression compression,
                                             final boolean valid) {
        super(protocol, serverCodings, clientCodings, compression, valid);
    }

    protected void assertResponse(final StreamingHttpResponse response) throws Throwable {
        verifyNoErrors();

        assertResponseHeaders(response.headers().get(CONTENT_ENCODING, identity().name()).toString());
    }

    @Override
    protected StreamingHttpResponse buildResponse(final StreamingHttpResponseFactory responseFactory) {
        return responseFactory.ok();
    }
}

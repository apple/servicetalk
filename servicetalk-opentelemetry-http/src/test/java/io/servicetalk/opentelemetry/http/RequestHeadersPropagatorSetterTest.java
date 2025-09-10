/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentelemetry.http;

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.netty.H2HeadersFactory;
import io.servicetalk.transport.api.ConnectionInfo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestHeadersPropagatorSetterTest {

    RequestInfo newRequestInfo(boolean http2, boolean withConnectionInfo) {
        HttpHeaders carrier = http2 ? H2HeadersFactory.INSTANCE.newHeaders() :
                DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        StreamingHttpRequest request = mock(StreamingHttpRequest.class);

        HttpProtocolVersion protocolVersion = http2 ?
                HttpProtocolVersion.HTTP_2_0 : HttpProtocolVersion.HTTP_1_1;
        when(request.headers()).thenReturn(carrier);
        when(request.version()).thenReturn(protocolVersion);

        ConnectionInfo connectionInfo = null;
        if (withConnectionInfo) {
            connectionInfo = mock(ConnectionInfo.class);
            when(connectionInfo.protocol()).thenReturn(protocolVersion);
        }
        return new RequestInfo(request, connectionInfo, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: http2={0}, withConnectionInfo={1}")
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void normalizedSetHeadersCaseInsensitive(boolean http2, boolean withConnectionInfo) {
        RequestInfo carrier = newRequestInfo(http2, withConnectionInfo);
        RequestHeadersPropagatorSetter.INSTANCE.set(carrier, "Foo", "bar");
        assertEquals("bar", carrier.request().headers().get("foo"));
        RequestHeadersPropagatorSetter.INSTANCE.set(carrier, "foo", "biz");
        assertEquals("biz", carrier.request().headers().get("foo"));
    }
}

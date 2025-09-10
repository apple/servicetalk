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
import io.servicetalk.http.netty.H2HeadersFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestHeadersPropagatorSetterTest {

    @ParameterizedTest(name = "{displayName} [{index}]: useH2Headers={0}")
    @ValueSource(booleans = {true, false})
    void setHeadersCaseInsensitive(boolean useH2Headers) {
        HttpHeaders carrier = useH2Headers ? H2HeadersFactory.INSTANCE.newHeaders() :
                DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        RequestHeadersPropagatorSetter.set(carrier, "Foo", "bar");
        assertEquals("bar", carrier.get("foo"));
        RequestHeadersPropagatorSetter.set(carrier, "foo", "biz");
        assertEquals("biz", carrier.get("foo"));
    }
}

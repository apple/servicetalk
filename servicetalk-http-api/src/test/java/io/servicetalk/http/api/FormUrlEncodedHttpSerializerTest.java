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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FormUrlEncodedHttpSerializerTest {
    @Test
    public void formParametersAreEncoded() {
        final FormUrlEncodedHttpSerializer serializer = FormUrlEncodedHttpSerializer.UTF8;

        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        final Map<String, List<String>> formParameters = new HashMap<>();
        formParameters.put("emptyParam", Collections.singletonList(""));
        formParameters.put("escape&this=", Collections.singletonList("and&this%"));
        formParameters.put("param2", Arrays.asList("foo", "bar"));

        final Buffer serialized = serializer.serialize(headers, formParameters, DEFAULT_ALLOCATOR);

        assertEquals("emptyParam=&escape%26this%3D=and%26this%25&param2=foo&param2=bar", serialized.toString(UTF_8));
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", headers.get(CONTENT_TYPE));
    }

    @Test
    public void serializeEmptyMap() {
        final FormUrlEncodedHttpSerializer serializer = FormUrlEncodedHttpSerializer.UTF8;

        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        final Map<String, List<String>> formParameters = Collections.emptyMap();

        final Buffer serialized = serializer.serialize(headers, formParameters, DEFAULT_ALLOCATOR);

        assertEquals(0 , serialized.capacity());
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", headers.get(CONTENT_TYPE));
    }

    @Test
    public void iterableCloseIsPropagated() throws Exception {
        final FormUrlEncodedHttpSerializer serializer = FormUrlEncodedHttpSerializer.UTF8;
        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        final AtomicBoolean isClosed = new AtomicBoolean(false);

        final BlockingIterable<Map<String, List<String>>> formParametersIterable = () -> new BlockingIterator<Map<String, List<String>>>() {
            @Override
            public boolean hasNext(final long timeout, final TimeUnit unit) {
                return false;
            }

            @Override
            public Map<String, List<String>> next(final long timeout, final TimeUnit unit) {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, List<String>> next() {
                return Collections.emptyMap();
            }

            @Override
            public void close() {
                isClosed.set(true);
            }

            @Override
            public boolean hasNext() {
                return false;
            }
        };

        final BlockingIterable<Buffer> serialized = serializer.serialize(headers, formParametersIterable, DEFAULT_ALLOCATOR);
        serialized.iterator().close();

        assertTrue(isClosed.get());
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", headers.get(CONTENT_TYPE));
    }
}

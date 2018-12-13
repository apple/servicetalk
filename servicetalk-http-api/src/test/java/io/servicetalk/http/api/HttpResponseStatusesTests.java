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

import org.junit.Test;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.SWITCHING_PROTOCOLS;
import static io.servicetalk.http.api.HttpResponseStatuses.getResponseStatus;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class HttpResponseStatusesTests {
    @Test
    public void reasonPhraseComparison() {
        assertThat(getResponseStatus(200, DEFAULT_ALLOCATOR.fromAscii("OK")), is(OK));
        assertThat(getResponseStatus(200, EMPTY_BUFFER), is(OK));
        assertThat(getResponseStatus(200, DEFAULT_ALLOCATOR.fromAscii("YES")), is(not(OK)));
    }

    @Test
    public void toStringRendering() {
        assertThat(SWITCHING_PROTOCOLS.toString(), is("101 Switching Protocols"));
        assertThat(getResponseStatus(555, DEFAULT_ALLOCATOR.fromAscii("Movie Area Code")).toString(),
                is("555 Movie Area Code"));
    }
}

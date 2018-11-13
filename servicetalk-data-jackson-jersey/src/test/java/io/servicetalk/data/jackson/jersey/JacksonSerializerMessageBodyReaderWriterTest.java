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
package io.servicetalk.data.jackson.jersey;

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.serialization.api.DefaultSerializer;
import io.servicetalk.serialization.api.SerializationException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Map;
import javax.ws.rs.BadRequestException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.data.jackson.jersey.JacksonSerializerMessageBodyReaderWriter.deserializeObject;
import static org.hamcrest.Matchers.instanceOf;

public class JacksonSerializerMessageBodyReaderWriterTest {
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expected = ExpectedException.none();

    @Test
    public void deserializeObjectDoesntRepeatBadRequestException() {
        expected.expect(instanceOf(BadRequestException.class));
        expected.expectCause(instanceOf(SerializationException.class));

        deserializeObject(from(DEFAULT_ALLOCATOR.fromAscii("{foo:123}")),
                new DefaultSerializer(new JacksonSerializationProvider()), Map.class, 9, DEFAULT_ALLOCATOR);
    }
}

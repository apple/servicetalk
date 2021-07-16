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
package io.servicetalk.serializer.utils;

import org.junit.jupiter.api.Test;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.serializer.utils.StringSerializer.stringSerializer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class FixedLengthStreamingSerializerTest {
    @Test
    void serializeDeserialize() throws Exception {
        FixedLengthStreamingSerializer<String> serializer = new FixedLengthStreamingSerializer<>(
                stringSerializer(UTF_8), String::length);

        assertThat(serializer.deserialize(serializer.serialize(from("foo", "bar"), DEFAULT_ALLOCATOR),
                DEFAULT_ALLOCATOR).toFuture().get(), contains("foo", "bar"));
    }
}

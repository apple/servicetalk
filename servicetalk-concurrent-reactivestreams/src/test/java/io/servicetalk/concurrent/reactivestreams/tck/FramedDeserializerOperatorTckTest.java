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
package io.servicetalk.concurrent.reactivestreams.tck;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.serializer.utils.FramedDeserializerOperator;

import org.testng.annotations.Test;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.util.function.Function.identity;

@Test
public class FramedDeserializerOperatorTckTest extends AbstractPublisherTckTest<Integer> {
    @Override
    public Publisher<Integer> createServiceTalkPublisher(final long elements) {
        return Publisher.range(0, TckUtils.requestNToInt(elements))
                .map(i -> DEFAULT_ALLOCATOR.newBuffer().writeInt(i))
                .liftSync(new FramedDeserializerOperator<>(
                        (serializedData, allocator) -> serializedData.readInt(),
                        () -> (buffer, bufferAllocator) ->
                                buffer.readableBytes() < Integer.BYTES ? null : buffer.readBytes(Integer.BYTES),
                        DEFAULT_ALLOCATOR))
                .flatMapConcatIterable(identity());
    }

    @Override
    public long maxElementsFromPublisher() {
        return TckUtils.maxElementsFromPublisher();
    }
}

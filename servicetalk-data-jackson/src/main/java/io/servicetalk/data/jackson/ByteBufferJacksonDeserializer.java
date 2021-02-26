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
package io.servicetalk.data.jackson;

import io.servicetalk.buffer.api.Buffer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.async.ByteBufferFeeder;
import com.fasterxml.jackson.databind.ObjectReader;

import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;

import static java.util.Collections.emptyList;

@Deprecated
final class ByteBufferJacksonDeserializer<T> extends AbstractJacksonDeserializer<T> {
    private final ByteBufferFeeder feeder;

    ByteBufferJacksonDeserializer(ObjectReader reader, JsonParser parser, ByteBufferFeeder feeder) {
        super(reader, parser);
        this.feeder = feeder;
    }

    @Override
    Iterable<T> doDeserialize(final Buffer buffer, @Nullable final List<T> resultHolder) throws IOException {
        feeder.feedInput(buffer.toNioBuffer());
        return !feeder.needMoreInput() ? consumeParserTokens(resultHolder) : emptyList();
    }

    @Override
    public void close() {
        feeder.endOfInput();
        super.close();
    }
}

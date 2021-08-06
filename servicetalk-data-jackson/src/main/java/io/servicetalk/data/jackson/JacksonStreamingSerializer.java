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
package io.servicetalk.data.jackson;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherOperator;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.async.ByteBufferFeeder;
import com.fasterxml.jackson.core.async.NonBlockingInputFeeder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;

import static com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static io.servicetalk.data.jackson.JacksonSerializer.doSerialize;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;

/**
 * Serialize and deserialize a stream of JSON objects.
 * @param <T> The type of objects to serialize.
 */
final class JacksonStreamingSerializer<T> implements StreamingSerializerDeserializer<T> {
    private final ObjectWriter writer;
    private final ObjectReader reader;

    JacksonStreamingSerializer(ObjectMapper mapper, Class<T> clazz) {
        writer = mapper.writerFor(clazz);
        reader = mapper.readerFor(clazz);
    }

    JacksonStreamingSerializer(ObjectMapper mapper, TypeReference<T> typeRef) {
        writer = mapper.writerFor(typeRef);
        reader = mapper.readerFor(typeRef);
    }

    JacksonStreamingSerializer(ObjectMapper mapper, JavaType type) {
        writer = mapper.writerFor(type);
        reader = mapper.readerFor(type);
    }

    @Override
    public Publisher<Buffer> serialize(final Publisher<T> toSerialize, final BufferAllocator allocator) {
        return toSerialize.map(t -> {
            Buffer buffer = allocator.newBuffer();
            doSerialize(writer, t, buffer);
            return buffer;
        });
    }

    @Override
    public Publisher<T> deserialize(final Publisher<Buffer> serializedData, final BufferAllocator allocator) {
        return serializedData.liftSync(new DeserializeOperator<T>(reader)).flatMapConcatIterable(identity());
    }

    private static final class DeserializeOperator<T> implements PublisherOperator<Buffer, Iterable<T>> {
        private final ObjectReader reader;

        private DeserializeOperator(ObjectReader reader) {
            this.reader = reader;
        }

        @Override
        public Subscriber<? super Buffer> apply(final Subscriber<? super Iterable<T>> subscriber) {
            final JsonParser parser;
            try {
                // TODO(scott): ByteBufferFeeder is currently not supported by jackson, and the current API throws
                // UnsupportedOperationException if not supported. When jackson does support two NonBlockingInputFeeder
                // types we need an approach which doesn't involve catching UnsupportedOperationException to try to get
                // ByteBufferFeeder and then ByteArrayFeeder.
                parser = reader.getFactory().createNonBlockingByteArrayParser();
            } catch (IOException e) {
                throw new SerializationException(e);
            }
            NonBlockingInputFeeder feeder = parser.getNonBlockingInputFeeder();
            if (feeder instanceof ByteBufferFeeder) {
                return new ByteBufferDeserializeSubscriber<>(subscriber, reader, parser, (ByteBufferFeeder) feeder);
            } else if (feeder instanceof ByteArrayFeeder) {
                return new ByteArrayDeserializeSubscriber<>(subscriber, reader, parser, (ByteArrayFeeder) feeder);
            }
            return new FailedSubscriber<>(subscriber, new SerializationException("unsupported feeder type: " + feeder));
        }

        private static final class ByteArrayDeserializeSubscriber<T> extends DeserializeSubscriber<T> {
            private final ByteArrayFeeder feeder;

            private ByteArrayDeserializeSubscriber(final Subscriber<? super Iterable<T>> subscriber,
                                                   final ObjectReader reader, final JsonParser parser,
                                                   final ByteArrayFeeder feeder) {
                super(subscriber, reader, parser);
                this.feeder = feeder;
            }

            @Override
            boolean consumeOnNext(final Buffer buffer) throws IOException {
                if (buffer.hasArray()) {
                    final int start = buffer.arrayOffset() + buffer.readerIndex();
                    feeder.feedInput(buffer.array(), start, start + buffer.readableBytes());
                } else {
                    int readableBytes = buffer.readableBytes();
                    if (readableBytes != 0) {
                        byte[] copy = new byte[readableBytes];
                        buffer.readBytes(copy);
                        feeder.feedInput(copy, 0, copy.length);
                    }
                }
                return feeder.needMoreInput();
            }
        }

        private static final class ByteBufferDeserializeSubscriber<T> extends DeserializeSubscriber<T> {
            private final ByteBufferFeeder feeder;

            private ByteBufferDeserializeSubscriber(final Subscriber<? super Iterable<T>> subscriber,
                                                    final ObjectReader reader, final JsonParser parser,
                                                    final ByteBufferFeeder feeder) {
                super(subscriber, reader, parser);
                this.feeder = feeder;
            }

            @Override
            boolean consumeOnNext(final Buffer buffer) throws IOException {
                feeder.feedInput(buffer.toNioBuffer());
                return feeder.needMoreInput();
            }
        }

        private abstract static class DeserializeSubscriber<T> implements Subscriber<Buffer> {
            private final JsonParser parser;
            private final ObjectReader reader;
            private final Deque<JsonNode> tokenStack = new ArrayDeque<>(8);
            private final Subscriber<? super Iterable<T>> subscriber;
            @Nullable
            private Subscription subscription;
            @Nullable
            private String fieldName;

            private DeserializeSubscriber(final Subscriber<? super Iterable<T>> subscriber,
                                          final ObjectReader reader,
                                          final JsonParser parser) {
                this.reader = reader;
                this.parser = parser;
                this.subscriber = subscriber;
            }

            /**
             * Consumer the buffer from {@link #onNext(Buffer)}.
             * @param buffer The bytes to append.
             * @return {@code true} if more data is required to parse an object. {@code false} if object(s) should be
             * parsed after this method returns.
             * @throws IOException If an exception occurs while appending {@link Buffer}.
             */
            abstract boolean consumeOnNext(Buffer buffer) throws IOException;

            @Override
            public final void onSubscribe(final Subscription subscription) {
                this.subscription = ConcurrentSubscription.wrap(subscription);
                subscriber.onSubscribe(this.subscription);
            }

            @Override
            public final void onNext(@Nullable final Buffer buffer) {
                assert subscription != null;
                try {
                    if (buffer == null || consumeOnNext(buffer)) {
                        subscription.request(1);
                    } else {
                        JsonToken token;
                        List<T> values = null;
                        T value = null;
                        while ((token = parser.nextToken()) != JsonToken.NOT_AVAILABLE) {
                            JsonNode nextRoot = push(token, parser);
                            if (nextRoot != null) {
                                if (values != null) {
                                    values.add(reader.readValue(nextRoot));
                                } else if (value == null) {
                                    value = reader.readValue(nextRoot);
                                } else {
                                    values = new ArrayList<>(3);
                                    values.add(value);
                                    value = null;
                                    values.add(reader.readValue(nextRoot));
                                }
                            }
                        }

                        if (values != null) {
                            subscriber.onNext(values);
                        } else if (value != null) {
                            subscriber.onNext(singletonList(value));
                        } else {
                            subscription.request(1);
                        }
                    }
                } catch (IOException e) {
                    throw new SerializationException(e);
                }
            }

            @Override
            public final void onError(final Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public final void onComplete() {
                if (tokenStack.isEmpty()) {
                    subscriber.onComplete();
                } else {
                    subscriber.onError(new SerializationException("completed with " + tokenStack.size() +
                            " tokens pending"));
                }
            }

            @Nullable
            private JsonNode push(JsonToken event, JsonParser parser) throws IOException {
                switch (event) {
                    case START_OBJECT:
                        tokenStack.push(createObject(tokenStack.peek()));
                        return null;
                    case START_ARRAY:
                        tokenStack.push(createArray(tokenStack.peek()));
                        return null;
                    case END_OBJECT:
                    case END_ARRAY:
                        JsonNode top = tokenStack.pop();
                        return tokenStack.isEmpty() ? top : null;
                    case FIELD_NAME:
                        assert !tokenStack.isEmpty();
                        fieldName = parser.getCurrentName();
                        return null;
                    case VALUE_STRING:
                        if (tokenStack.isEmpty()) {
                            return new TextNode(parser.getValueAsString());
                        }
                        addValue(tokenStack.peek(), parser.getValueAsString());
                        return null;
                    case VALUE_NUMBER_INT:
                        // Ideally we want to make sure that if we deserialize a single primitive value, that is the
                        // only thing that this deserializer deserializes, i.e. any subsequent deserialization attempts
                        // MUST throw. However, to achieve that we need to maintain state between two deserialize calls.
                        // Jackson does not support deserializing a single primitive number as yet when used with
                        // non-blocking parser. Hence, we avoid doing that state management yet.
                        addValue(peekNonNull(), parser.getLongValue());
                        return null;
                    case VALUE_NUMBER_FLOAT:
                        addValue(peekNonNull(), parser.getDoubleValue());
                        return null;
                    case VALUE_TRUE:
                        if (tokenStack.isEmpty()) {
                            return BooleanNode.TRUE;
                        }
                        addValue(tokenStack.peek(), true);
                        return null;
                    case VALUE_FALSE:
                        if (tokenStack.isEmpty()) {
                            return BooleanNode.FALSE;
                        }
                        addValue(tokenStack.peek(), false);
                        return null;
                    case VALUE_NULL:
                        if (tokenStack.isEmpty()) {
                            return NullNode.getInstance();
                        }
                        addNull(tokenStack.peek());
                        return null;
                    default:
                        throw new IllegalArgumentException("unsupported event: " + event);
                }
            }

            private JsonNode peekNonNull() {
                JsonNode node = tokenStack.peek();
                assert node != null;
                return node;
            }

            private JsonNode createObject(@Nullable JsonNode current) {
                if (current instanceof ObjectNode) {
                    return ((ObjectNode) current).putObject(fieldName);
                } else if (current instanceof ArrayNode) {
                    return ((ArrayNode) current).addObject();
                } else {
                    return instance.objectNode();
                }
            }

            private JsonNode createArray(@Nullable JsonNode current) {
                if (current instanceof ObjectNode) {
                    return ((ObjectNode) current).putArray(fieldName);
                } else if (current instanceof ArrayNode) {
                    return ((ArrayNode) current).addArray();
                } else {
                    return instance.arrayNode();
                }
            }

            private void addValue(JsonNode current, String s) {
                if (current instanceof ObjectNode) {
                    ((ObjectNode) current).put(fieldName, s);
                } else {
                    ((ArrayNode) current).add(s);
                }
            }

            private void addValue(JsonNode current, long v) {
                if (current instanceof ObjectNode) {
                    ((ObjectNode) current).put(fieldName, v);
                } else {
                    ((ArrayNode) current).add(v);
                }
            }

            private void addValue(JsonNode current, double v) {
                if (current instanceof ObjectNode) {
                    ((ObjectNode) current).put(fieldName, v);
                } else {
                    ((ArrayNode) current).add(v);
                }
            }

            private void addValue(JsonNode current, boolean v) {
                if (current instanceof ObjectNode) {
                    ((ObjectNode) current).put(fieldName, v);
                } else {
                    ((ArrayNode) current).add(v);
                }
            }

            private void addNull(JsonNode current) {
                if (current instanceof ObjectNode) {
                    ((ObjectNode) current).putNull(fieldName);
                } else {
                    ((ArrayNode) current).addNull();
                }
            }
        }

        private static final class FailedSubscriber<T> implements Subscriber<Buffer> {
            private final SerializationException exception;
            private final Subscriber<? super Iterable<T>> subscriber;

            private FailedSubscriber(final Subscriber<? super Iterable<T>> subscriber,
                                     final SerializationException exception) {
                this.subscriber = subscriber;
                this.exception = exception;
            }

            @Override
            public void onSubscribe(final Subscription subscription) {
                try {
                    subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                } catch (Throwable cause) {
                    subscriber.onError(cause);
                    return;
                }
                subscriber.onError(exception);
            }

            @Override
            public void onNext(@Nullable final Buffer buffer) {
            }

            @Override
            public void onError(final Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        }
    }
}

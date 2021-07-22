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
import io.servicetalk.serialization.api.SerializationException;
import io.servicetalk.serialization.api.StreamingDeserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
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
import static java.util.Collections.emptyList;

@Deprecated
abstract class AbstractJacksonDeserializer<T> implements StreamingDeserializer<T> {

    private final Deque<JsonNode> nodeStack = new ArrayDeque<>();
    private final ObjectReader reader;
    private final JsonParser parser;

    @Nullable
    private String fieldName;

    AbstractJacksonDeserializer(ObjectReader reader, JsonParser parser) {
        this.reader = reader;
        this.parser = parser;
    }

    @Override
    public final Iterable<T> deserialize(final Buffer toDeserialize) {
        try {
            return doDeserialize(toDeserialize, null);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public Iterable<T> deserialize(final Iterable<Buffer> buffers) {
        final List<T> toReturn = new ArrayList<>(2);
        for (Buffer buffer : buffers) {
            try {
                doDeserialize(buffer, toReturn);
            } catch (IOException e) {
                throw new SerializationException(e);
            }
        }
        return toReturn;
    }

    @Override
    public final boolean hasData() {
        // Jackson API does not currently have a way to determine whether there is left over data inside the parser
        // for which there is no token than has been consumed by consumeParserTokens().
        // For arrays and objects, this works as we always get a START_OBJECT or START_ARRAY token which stays in the
        // node stack till that object/array is fully deserialized.
        // However for standalone primitive types, there is no token emitted till the whole primitive is parsed.
        // This makes it such that if a standalone primitive type is split across buffers, then we do not know, whether
        // we have started parsing a primitive or there is no data to parse. In such cases, we err on the side of
        // caution and assume there is no left over data.
        return !nodeStack.isEmpty();
    }

    @Override
    public void close() {
        if (hasData()) {
            throw new SerializationException("Left over data in the serializer on close.");
        }
    }

    abstract Iterable<T> doDeserialize(Buffer buffer, @Nullable List<T> resultHolder) throws IOException;

    final List<T> consumeParserTokens(@Nullable List<T> resultHolder) throws IOException {
        JsonToken token = parser.nextToken();
        if (token == JsonToken.NOT_AVAILABLE) {
            // Avoid creating list if there are no items available.
            return resultHolder == null ? emptyList() : resultHolder;
        }
        List<T> toReturn = resultHolder == null ? new ArrayList<>(2) : resultHolder;
        do {
            JsonNode nextRoot = push(token, parser);
            if (nextRoot != null) {
                toReturn.add(reader.readValue(nextRoot));
            }
        } while ((token = parser.nextToken()) != JsonToken.NOT_AVAILABLE);
        return toReturn;
    }

    @Nullable
    final JsonNode push(JsonToken event, JsonParser parser) throws IOException {
        switch (event) {
            case START_OBJECT:
                nodeStack.push(createObject(nodeStack.peek()));
                return null;
            case START_ARRAY:
                nodeStack.push(createArray(nodeStack.peek()));
                return null;
            case END_OBJECT:
            case END_ARRAY:
                JsonNode top = nodeStack.pop();
                return nodeStack.isEmpty() ? top : null;
            case FIELD_NAME:
                assert !nodeStack.isEmpty();
                fieldName = parser.getCurrentName();
                return null;
            case VALUE_STRING:
                if (nodeStack.isEmpty()) {
                    return new TextNode(parser.getValueAsString());
                }
                addValue(nodeStack.peek(), parser.getValueAsString());
                return null;
            case VALUE_NUMBER_INT:
                // Ideally we want to make sure that if we deserialize a single primitive value, that is the only thing
                // that this deserializer deserializes, i.e. any subsequent deserialization attempts MUST throw.
                // However, to achieve that we need to maintain state between two deserialize calls. Jackson does not
                // support deserializing a single primitive number as yet when used with non-blocking parser. Hence,
                // we avoid doing that state management yet.
                addValue(peekNonNull(), parser.getLongValue());
                return null;
            case VALUE_NUMBER_FLOAT:
                addValue(peekNonNull(), parser.getDoubleValue());
                return null;
            case VALUE_TRUE:
                if (nodeStack.isEmpty()) {
                    return BooleanNode.TRUE;
                }
                addValue(nodeStack.peek(), true);
                return null;
            case VALUE_FALSE:
                if (nodeStack.isEmpty()) {
                    return BooleanNode.FALSE;
                }
                addValue(nodeStack.peek(), false);
                return null;
            case VALUE_NULL:
                if (nodeStack.isEmpty()) {
                    return NullNode.getInstance();
                }
                addNull(nodeStack.peek());
                return null;
            default:
                throw new IllegalArgumentException("unsupported event: " + event);
        }
    }

    private JsonNode peekNonNull() {
        JsonNode node = nodeStack.peek();
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

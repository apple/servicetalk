/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.netty.BufferAllocators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.data.jackson.JacksonSerializerFactory.JACKSON;

/**
 * Performs (de)serialization benchmarks on the {@link JacksonStreamingSerializer}.
 *
 * This benchmark got added when trying to understand if the NonBlockingByteArrayParser which Jackson added in
 * 2.14.0 would give us performance benefits on deserialization. Unfortunately based on the numbers below there
 * does not seem to be a benefit at the time of benchmarking (but with newer jackson versions there might be
 * in the future!)
 *
 * ST 0.42.22:
 *
 * Benchmark                                                            Mode  Cnt        Score      Error  Units
 * JacksonStreamingSerializerBenchmark.deserializeLargeBackedByArray   thrpt    5    99499,883 ±  976,339  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeLargeBackedByDirect  thrpt    5   101275,913 ± 2474,497  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeMidBackedByArray     thrpt    5   255373,032 ± 1189,892  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeMidBackedByDirect    thrpt    5   268339,262 ± 3017,273  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeSmallBackedByArray   thrpt    5  1367060,052 ± 3146,703  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeSmallBackedByDirect  thrpt    5   858069,490 ± 1623,354  ops/s
 *
 * Using the NonBlockingByteBufferParser exclusively:
 *
 * Benchmark                                                            Mode  Cnt       Score      Error  Units
 * JacksonStreamingSerializerBenchmark.deserializeLargeBackedByArray   thrpt    5   92993,855 ± 1456,355  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeLargeBackedByDirect  thrpt    5  101881,589 ± 1823,040  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeMidBackedByArray     thrpt    5  272582,163 ± 1413,955  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeMidBackedByDirect    thrpt    5  248119,214 ± 2427,704  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeSmallBackedByArray   thrpt    5  888589,926 ± 2513,038  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeSmallBackedByDirect  thrpt    5  797724,354 ± 1449,287  ops/s
 *
 * A mixture of both where on arrival of the first Buffer the parser is decided:
 *
 * Benchmark                                                            Mode  Cnt        Score      Error  Units
 * JacksonStreamingSerializerBenchmark.deserializeLargeBackedByArray   thrpt    5   102008,226 ± 1701,712  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeLargeBackedByDirect  thrpt    5    97443,190 ±  880,327  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeMidBackedByArray     thrpt    5   283652,034 ± 3478,137  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeMidBackedByDirect    thrpt    5   250312,647 ± 2946,121  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeSmallBackedByArray   thrpt    5  1092601,832 ± 3884,520  ops/s
 * JacksonStreamingSerializerBenchmark.deserializeSmallBackedByDirect  thrpt    5   727287,804 ± 2357,798  ops/s
 */
@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@BenchmarkMode(Mode.Throughput)
public class JacksonStreamingSerializerBenchmark {

    /**
     * Allows to customize the number of chunks the payload is split up to simulate JSON chunk stream decoding.
     */
    private static final int NUM_CHUNKS = 8;

    /**
     * Type reference used for deserialization mapping.
     */
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
            new TypeReference<Map<String, Object>>() {
            };

    private static final String SMALL_JSON = generateJson(10);
    private static final String MID_JSON = generateJson(1_000);
    private static final String LARGE_JSON = generateJson(1_000_000);

    private static final Buffer SMALL_JSON_HEAP = BufferAllocators.PREFER_HEAP_ALLOCATOR.fromUtf8(SMALL_JSON);
    private static final Buffer SMALL_JSON_DIRECT = BufferAllocators.PREFER_DIRECT_ALLOCATOR.fromUtf8(SMALL_JSON);

    private static final Buffer MID_JSON_HEAP = BufferAllocators.PREFER_HEAP_ALLOCATOR.fromUtf8(MID_JSON);
    private static final Buffer MID_JSON_DIRECT = BufferAllocators.PREFER_DIRECT_ALLOCATOR.fromUtf8(MID_JSON);

    private static final Buffer LARGE_JSON_HEAP = BufferAllocators.PREFER_HEAP_ALLOCATOR.fromUtf8(LARGE_JSON);
    private static final Buffer LARGE_JSON_DIRECT = BufferAllocators.PREFER_DIRECT_ALLOCATOR.fromUtf8(LARGE_JSON);

    @Benchmark
    public Map<String, Object> deserializeSmallBackedByArray() {
        return deserialize(SMALL_JSON_HEAP.duplicate());
    }

    @Benchmark
    public Map<String, Object> deserializeSmallBackedByDirect() {
        return deserialize(SMALL_JSON_DIRECT.duplicate());
    }

    @Benchmark
    public Map<String, Object> deserializeMidBackedByArray() {
        return deserialize(MID_JSON_HEAP.duplicate());
    }

    @Benchmark
    public Map<String, Object> deserializeMidBackedByDirect() {
        return deserialize(MID_JSON_DIRECT.duplicate());
    }

    @Benchmark
    public Map<String, Object> deserializeLargeBackedByArray() {
        return deserialize(LARGE_JSON_HEAP.duplicate());
    }

    @Benchmark
    public Map<String, Object> deserializeLargeBackedByDirect() {
        return deserialize(LARGE_JSON_DIRECT.duplicate());
    }

    private static Map<String, Object> deserialize(final Buffer fromBuffer) {
        int chunkSize = fromBuffer.readableBytes() / NUM_CHUNKS;
        List<Buffer> chunks = new ArrayList<>();
        while (fromBuffer.readableBytes() > chunkSize) {
            chunks.add(fromBuffer.readSlice(chunkSize));
        }
        chunks.add(fromBuffer.readSlice(fromBuffer.readableBytes()));

        try {
            return JACKSON
                    .streamingSerializerDeserializer(MAP_TYPE_REFERENCE)
                    .deserialize(fromIterable(chunks), BufferAllocators.DEFAULT_ALLOCATOR)
                    .firstOrError()
                    .toFuture()
                    .get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates a primitive encoded JSON object with roughly the size provided as argument.
     * <p>
     * Note that the returned encoded JSON does not have exactly the size provided, since the size is used to randomly
     * generate keys and values. The additional bytes in the returned string are JSON structure symbols. Also, a maximum
     * of 50 bytes per key and value is chosen to prevent large payloads from looking very unbalanced in
     * key/value sizes.
     *
     * @param size the approx size for the generated JSON payload.
     * @return the encoded JSON object.
     */
    private static String generateJson(final int size) {
        final Map<String, Object> payload = new HashMap<>();

        int remainingBytes = size;
        Random random = new Random();
        while (remainingBytes > 2) {
            int keyLen = random.nextInt(1, Math.min(50, remainingBytes));
            remainingBytes -= keyLen;
            if (remainingBytes <= 2) {
                break;
            }
            int bodyLen = random.nextInt(1, Math.min(50, remainingBytes));
            remainingBytes -= bodyLen;

            payload.put(
                    String.join("", Collections.nCopies(keyLen, "k")),
                    String.join("", Collections.nCopies(bodyLen, "v"))
            );
        }

        try {
            return new ObjectMapper().writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}

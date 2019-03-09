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
package io.servicetalk.benchmark.concurrent;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.ConnectableOutputStream;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.util.Random;

import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;

/**
 * Multi-threaded benchmark of {@link ConnectableOutputStream} with various data sizes and flush strategies which
 * attempts to simulate some contention on the locks.
 * <p>
 * <pre>
 * Sample run comparing throughput of minimum vs double on resize strategy and flush on each vs 30% of writes.
 *
 * Benchmark            (dataSize)  (flushOnEach)   Mode  Cnt            Score            Error  Units
 * group                      1000           true  thrpt    3     14420531.924 ±    1643793.333  ops/s
 * group:consumedBytes        1000           true  thrpt    3   2283217869.979 ±  166557144.957  ops/s
 * group:flush                1000           true  thrpt    3      2283210.042 ±     166445.653  ops/s
 * group:onNext               1000           true  thrpt    3      2282745.343 ±     167246.663  ops/s
 * group:producedBytes        1000           true  thrpt    3   2283210041.849 ±  166445652.838  ops/s
 * group:requestN             1000           true  thrpt    3     12137336.948 ±    1507746.502  ops/s
 * group:write                1000           true  thrpt    3      2283194.976 ±     166158.336  ops/s
 * group                      1000          false  thrpt    3     49706282.158 ±    8563620.180  ops/s
 * group:consumedBytes        1000          false  thrpt    3   2002143421.593 ±  757039466.941  ops/s
 * group:flush                1000          false  thrpt    3       600502.218 ±     228788.285  ops/s
 * group:onNext               1000          false  thrpt    3       600439.140 ±     228961.389  ops/s
 * group:producedBytes        1000          false  thrpt    3   2002142728.565 ±  757003892.414  ops/s
 * group:requestN             1000          false  thrpt    3     47704143.828 ±    7812104.236  ops/s
 * group:write                1000          false  thrpt    3      2002138.330 ±     756940.582  ops/s
 * group                    100000           true  thrpt    3     97975038.177 ±   18791096.475  ops/s
 * group:consumedBytes      100000           true  thrpt    3  12542736164.169 ± 3003674779.827  ops/s
 * group:flush              100000           true  thrpt    3       125427.106 ±      30033.759  ops/s
 * group:onNext             100000           true  thrpt    3       125425.828 ±      30018.489  ops/s
 * group:producedBytes      100000           true  thrpt    3  12542710616.019 ± 3003375884.451  ops/s
 * group:requestN           100000           true  thrpt    3     97849611.270 ±   18761968.538  ops/s
 * group:write              100000           true  thrpt    3       125426.906 ±      30033.758  ops/s
 * group                    100000          false  thrpt    3    103361261.892 ±   18055706.399  ops/s
 * group:consumedBytes      100000          false  thrpt    3   3271179635.437 ±  360722125.720  ops/s
 * group:flush              100000          false  thrpt    3         9807.664 ±        484.184  ops/s
 * group:onNext             100000          false  thrpt    3         9807.717 ±        485.220  ops/s
 * group:producedBytes      100000          false  thrpt    3   3271302081.604 ±  360223642.512  ops/s
 * group:requestN           100000          false  thrpt    3    103328549.071 ±   18052205.808  ops/s
 * group:write              100000          false  thrpt    3        32712.821 ±       3603.743  ops/s
 * group                   1000000           true  thrpt    3     98910550.896 ±   21466300.054  ops/s
 * group:consumedBytes     1000000           true  thrpt    3   7885392958.202 ± 1236140367.226  ops/s
 * group:flush             1000000           true  thrpt    3         7885.269 ±       1231.961  ops/s
 * group:onNext            1000000           true  thrpt    3         7885.393 ±       1236.140  ops/s
 * group:producedBytes     1000000           true  thrpt    3   7885268807.291 ± 1231961205.779  ops/s
 * group:requestN          1000000           true  thrpt    3     98902665.693 ±   21466216.610  ops/s
 * group:write             1000000           true  thrpt    3         7885.202 ±       1233.901  ops/s
 * group                   1000000          false  thrpt    3     93467865.623 ±   24681285.758  ops/s
 * group:consumedBytes     1000000          false  thrpt    3   1674200698.407 ± 1191352568.112  ops/s
 * group:flush             1000000          false  thrpt    3          503.387 ±        437.377  ops/s
 * group:onNext            1000000          false  thrpt    3          503.433 ±        438.518  ops/s
 * group:producedBytes     1000000          false  thrpt    3   1675515746.200 ± 1184491875.465  ops/s
 * group:requestN          1000000          false  thrpt    3     93466190.240 ±   24680358.208  ops/s
 * group:write             1000000          false  thrpt    3         1675.382 ±       1185.913  ops/s * </pre>
 */
@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 3, time = 5)
@BenchmarkMode(Mode.Throughput)
public class ConnectableOutputStreamBenchmark {

    @Param({"1000", "100000", "1000000"})
    public int dataSize;

    @Param({"true", "false"})
    public boolean flushOnEach;

    final Random r = new Random();
    byte[] data;
    ConnectableOutputStream cos;
    Publisher<Buffer> publisher;
    Subscription subscription;

    @Setup(Level.Iteration)
    public void setup() {
        data = new byte[dataSize];
        cos = new ConnectableOutputStream(PREFER_HEAP_ALLOCATOR);
        publisher = cos.connect();
        // Don't remove this, JMH somehow provides a default which break everything
        subscription = null;
    }

    @AuxCounters
    @State(Scope.Thread)
    public static class ProducerCounter {
        public long producedBytes;
        public long flush;

        @Setup(Level.Iteration)
        public void clean() {
            producedBytes = 0;
            flush = 0;
        }
    }

    @AuxCounters
    @State(Scope.Thread)
    public static class ConsumerCounter {
        public long consumedBytes;
        public long onNext;

        @Setup(Level.Iteration)
        public void clean() {
            consumedBytes = 0;
            onNext = 0;
        }
    }

    @Benchmark
    @Group
    public void write(ProducerCounter counter) throws IOException {
        cos.write(data);
        counter.producedBytes += dataSize;
        if (flushOnEach || r.nextInt(100) < 30) {
            cos.flush();
            counter.flush++;
        }
    }

    @Benchmark
    @Group
    public void requestN(ConsumerCounter counter) {
        if (subscription == null) {
            toSource(publisher).subscribe(new Subscriber<Buffer>() {
                @Override
                public void onSubscribe(final Subscription s) {
                    subscription = s;
                }

                @Override
                public void onNext(final Buffer buffer) {
                    counter.consumedBytes += buffer.readableBytes();
                    ++counter.onNext;
                }

                @Override
                public void onError(final Throwable t) {
                }

                @Override
                public void onComplete() {
                }
            });
        }
        subscription.request(1);
    }
}

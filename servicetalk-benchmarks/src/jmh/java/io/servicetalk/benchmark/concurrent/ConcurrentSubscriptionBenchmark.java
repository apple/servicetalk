/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@BenchmarkMode(Mode.Throughput)
public class ConcurrentSubscriptionBenchmark {
    private final InnerSubscription innerSubscription = new InnerSubscription();
    private final Subscription subscription = ConcurrentSubscription.wrap(innerSubscription);

    @Benchmark
    public long singleThread() {
        subscription.request(1);
        return innerSubscription.requestN;
    }

    @Group("multiThread")
    @GroupThreads(2)
    @Benchmark
    public long multiThread() {
        subscription.request(1);
        return innerSubscription.requestN;
    }

    private static final class InnerSubscription implements Subscription {
        private long requestN;

        @Override
        public void request(final long n) {
            requestN += n;
        }

        @Override
        public void cancel() {
        }
    }
}

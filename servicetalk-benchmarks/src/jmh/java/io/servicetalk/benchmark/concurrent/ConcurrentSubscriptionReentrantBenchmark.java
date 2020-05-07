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
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import javax.annotation.Nullable;

@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@BenchmarkMode(Mode.Throughput)
public class ConcurrentSubscriptionReentrantBenchmark {
    @Param({"2", "10", "100"})
    public int stackDepth;
    private InnerSubscription innerSubscription;
    private Subscription subscription;

    @Setup(Level.Iteration)
    public void setup() {
        innerSubscription = new InnerSubscription(stackDepth);
        subscription = ConcurrentSubscription.wrap(innerSubscription);
        innerSubscription.outerSubscription(subscription);
    }

    @Benchmark
    public long singleThread() {
        innerSubscription.reentrantCount = 0;
        subscription.request(1);
        return innerSubscription.requestN;
    }

    private static final class InnerSubscription implements Subscription {
        private long requestN;
        private int reentrantCount;
        private final int reentrantLimit;
        @Nullable
        private Subscription outerSubscription;

        private InnerSubscription(final int reentrantLimit) {
            this.reentrantLimit = reentrantLimit;
        }

        void outerSubscription(Subscription s) {
            outerSubscription = s;
        }

        @Override
        public void request(final long n) {
            assert outerSubscription != null;
            requestN += n;
            if (++reentrantCount < reentrantLimit) {
                outerSubscription.request(1);
            }
        }

        @Override
        public void cancel() {
        }
    }
}

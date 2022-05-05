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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static io.servicetalk.http.api.HttpSerializers.appSerializerAsciiFixLen;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

final class BlockingStreamingHttpClientTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void iterableSequentialConsumptionDoesNotDeadLock(int numItems) throws Exception {
        try (ServerContext ctx = HttpServers.forAddress(localAddress(0))
                .listenBlockingStreamingAndAwait((ctx1, request, response) -> {
                    HttpPayloadWriter<String> output = response.sendMetaData(appSerializerAsciiFixLen());
                    for (String input : request.payloadBody(appSerializerAsciiFixLen())) {
                        output.write(input);
                    }
                    output.close();
                });
             BlockingStreamingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(ctx))
                     .buildBlockingStreaming()) {
            NextSuppliers<String> nextSuppliers = NextSuppliers.stringSuppliers(numItems);
            // Allow first item in Iterator to return from next().
            nextSuppliers.countDownNextLatch();
            BlockingStreamingHttpResponse resp = client.request(client.get("/")
                    .payloadBody(new TestBlockingIterable<>(nextSuppliers, nextSuppliers), appSerializerAsciiFixLen()));
            StringBuilder responseBody = new StringBuilder(numItems);
            int i = 0;
            for (String respChunk : resp.payloadBody(appSerializerAsciiFixLen())) {
                // Goal is to ensure we can write each individual chunk independently without blocking threads or having
                // to batch multiple items. As each chunk is echoed back, unblock the next one.
                nextSuppliers.countDownNextLatch();
                responseBody.append(respChunk);
                ++i;
            }
            assertThat("num items: " + i + " responseBody: " + responseBody, i, equalTo(numItems));
        }
    }

    private static final class NextSuppliers<T> implements Supplier<T>, BooleanSupplier {
        private final List<NextSupplier<T>> items;
        private final AtomicInteger nextIndex = new AtomicInteger();
        private final AtomicInteger latchIndex = new AtomicInteger();

        NextSuppliers(List<NextSupplier<T>> items) {
            this.items = items;
        }

        static NextSuppliers<String> stringSuppliers(final int numItems) {
            List<NextSupplier<String>> items = new ArrayList<>(numItems);
            for (int i = 0; i < numItems; ++i) {
                items.add(new NextSupplier<>(String.valueOf(i)));
            }
            return new NextSuppliers<>(items);
        }

        void countDownNextLatch() {
            final int i = latchIndex.getAndIncrement();
            if (i < items.size()) {
                items.get(i).latch.countDown();
            }
        }

        @Override
        public boolean getAsBoolean() {
            return nextIndex.get() < items.size();
        }

        @Override
        public T get() {
            return items.get(nextIndex.getAndIncrement()).get();
        }
    }

    private static final class NextSupplier<T> implements Supplier<T> {
        final CountDownLatch latch = new CountDownLatch(1);
        final T next;

        private NextSupplier(final T next) {
            this.next = next;
        }

        @Override
        public T get() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throwException(e);
            }
            return next;
        }
    }

    private static final class TestBlockingIterable<T> implements Iterable<T> {
        private final BooleanSupplier hasNextSupplier;
        private final Supplier<T> nextSupplier;

        private TestBlockingIterable(final BooleanSupplier hasNextSupplier, final Supplier<T> nextSupplier) {
            this.hasNextSupplier = hasNextSupplier;
            this.nextSupplier = nextSupplier;
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    return hasNextSupplier.getAsBoolean();
                }

                @Override
                public T next() {
                    return nextSupplier.get();
                }
            };
        }
    }
}

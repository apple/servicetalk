/*
 * Copyright © 2022-2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_NEVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeoutHttpRequesterFilterTest extends AbstractNettyHttpServerTest {

    private enum Event {
        ON_CLOSING, ON_ERROR, ON_COMPLETE, CANCEL
    }

    private final AtomicReference<Event> firstEventRef = new AtomicReference<>();

    @Test
    void onClosingWinsOnError() throws Exception {
        connectionFilterFactory(appendConnectionFilter(c -> {
                    c.connectionContext().onClosing()
                            .subscribe(() -> firstEventRef.compareAndSet(null, Event.ON_CLOSING));
                    return new StreamingHttpConnectionFilter(c) {
                        @Override
                        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                            return delegate().request(request)
                                    .liftSync(new BeforeFinallyHttpOperator(new TerminalSignalConsumer() {
                                        @Override
                                        public void onComplete() {
                                            firstEventRef.compareAndSet(null, Event.ON_COMPLETE);
                                        }

                                        @Override
                                        public void onError(final Throwable throwable) {
                                            firstEventRef.compareAndSet(null, Event.ON_ERROR);
                                        }

                                        @Override
                                        public void cancel() {
                                            firstEventRef.compareAndSet(null, Event.CANCEL);
                                        }
                                    }));
                        }
                    };
                },
                new TimeoutHttpRequesterFilter(Duration.ofMillis(100))));
        setUp(CACHED, CACHED_SERVER);

        StreamingHttpClient client = streamingHttpClient();
        // Verify that ON_CLOSING always wins
        for (int i = 0; i < 15; i++) {
            StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get();
            String msg = "Unexpected assertion on iteration #" + i;
            ExecutionException ee = assertThrows(ExecutionException.class,
                    () -> connection.request(connection.get(SVC_NEVER)).toFuture().get(), msg);
            assertThat(msg, ee.getCause(), is(instanceOf(TimeoutException.class)));
            assertThat(msg, firstEventRef.get(), is(Event.ON_CLOSING));
            // Cancel always closes HTTP/1.x connection, wait for connection to close before trying again
            connection.onClose().toFuture().get();
        }
    }

    @Test
    void h2TimeoutResetsStreamWithoutClosingConnection() throws Exception {
        final AtomicInteger connections = new AtomicInteger();
        final AtomicBoolean connectionClosing = new AtomicBoolean();
        protocol(HTTP_2.config);
        connectionFilterFactory(appendConnectionFilter(c -> {
                    connections.incrementAndGet();
                    c.connectionContext().onClosing().subscribe(() -> connectionClosing.set(true));
                    return new StreamingHttpConnectionFilter(c) { };
                },
                new TimeoutHttpRequesterFilter(Duration.ofMillis(100))));
        setUp(CACHED, CACHED_SERVER);
        // Release the reserved connection so the client reuses it instead of opening another.
        ((ReservedStreamingHttpConnection) streamingHttpConnection()).releaseAsync().toFuture().get();
        final int connectionsBefore = connections.get();

        StreamingHttpClient client = streamingHttpClient();
        for (int i = 0; i < 15; i++) {
            String msg = "Unexpected assertion on iteration #" + i;
            ExecutionException ee = assertThrows(ExecutionException.class,
                    () -> client.request(client.get(SVC_NEVER)).toFuture().get(), msg);
            assertThat(msg, ee.getCause(), is(instanceOf(TimeoutException.class)));
            StreamingHttpResponse response = client.request(client.get(SVC_ECHO)).toFuture().get();
            assertThat(msg, response.status(), is(OK));
            response.messageBody().ignoreElements().toFuture().get();
        }
        assertThat(connectionClosing.get(), is(false));
        assertThat(connections.get(), is(connectionsBefore));
    }

    private static StreamingHttpConnectionFilterFactory appendConnectionFilter(
            final StreamingHttpConnectionFilterFactory first,
            final StreamingHttpConnectionFilterFactory next) {
        return connection -> first.create(next.create(connection));
    }
}

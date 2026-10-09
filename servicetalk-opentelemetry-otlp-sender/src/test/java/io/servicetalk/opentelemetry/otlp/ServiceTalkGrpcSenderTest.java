/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentelemetry.otlp;

import io.servicetalk.http.api.StreamingHttpClient;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.export.MessageWriter;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

final class ServiceTalkGrpcSenderTest {

    private static final String FULL_METHOD = "opentelemetry.proto.collector.trace.v1.TraceService/Export";

    // A no-op writer is enough for the shutdown paths, which never reach the wire.
    private static final MessageWriter EMPTY_MESSAGE = new MessageWriter() {
        @Override
        public void writeMessage(OutputStream output) {
        }

        @Override
        public int getContentLength() {
            return 0;
        }
    };

    @Test
    void shutdownIsIdempotent() throws Exception {
        ServiceTalkGrpcSender sender = newSender();

        CompletableResultCode first = sender.shutdown();
        first.join(10, TimeUnit.SECONDS);
        assertThat("first shutdown closes the client", first.isSuccess(), is(true));

        // Second call must short-circuit to success without touching the already-closed client.
        assertThat("second shutdown is a no-op success", sender.shutdown().isSuccess(), is(true));
    }

    @Test
    void sendAfterShutdownFailsFast() throws Exception {
        ServiceTalkGrpcSender sender = newSender();
        sender.shutdown().join(10, TimeUnit.SECONDS);

        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean responded = new AtomicBoolean();
        sender.send(EMPTY_MESSAGE, response -> responded.set(true), error::set);

        assertThat("no response is produced after shutdown", responded.get(), is(false));
        assertThat(error.get(), is(instanceOf(IllegalStateException.class)));
    }

    @Test
    void sendReportsMarshallingFailureThroughOnError() {
        // send()'s contract is that failures arrive via onError, not by throwing at the exporter. A
        // marshaler that throws synchronously must be routed to onError.
        ServiceTalkGrpcSender sender = newSender();
        MessageWriter throwingWriter = new MessageWriter() {
            @Override
            public void writeMessage(OutputStream output) {
                throw new IllegalStateException("marshal boom");
            }

            @Override
            public int getContentLength() {
                return 0;
            }
        };

        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean responded = new AtomicBoolean();
        sender.send(throwingWriter, response -> responded.set(true), error::set);

        assertThat("no response is produced when marshalling fails", responded.get(), is(false));
        assertThat(error.get(), is(instanceOf(IllegalStateException.class)));
    }

    private static ServiceTalkGrpcSender newSender() {
        // No collector is started: the shutdown and marshalling-failure paths never open a connection,
        // so a lazily-connecting client is enough.
        StreamingHttpClient client = ServiceTalkHttpClientFactory.buildGrpcClient(
                URI.create("http://localhost:1"), null, null, null, null, null);
        return new ServiceTalkGrpcSender(client, null, FULL_METHOD);
    }
}

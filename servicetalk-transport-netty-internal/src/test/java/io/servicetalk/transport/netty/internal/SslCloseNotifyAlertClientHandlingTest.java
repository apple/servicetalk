/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.test.StepVerifiers;

import org.junit.Test;

import java.nio.channels.ClosedChannelException;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;

public class SslCloseNotifyAlertClientHandlingTest extends AbstractSslCloseNotifyAlertHandlingTest {

    public SslCloseNotifyAlertClientHandlingTest() throws Exception {
        super(true);
    }

    @Test
    public void afterExchangeIdleConnection() {
        sendRequest();
        StepVerifiers.create(conn.read())
                .then(() -> channel.writeInbound(BEGIN))
                .expectNext(BEGIN)
                .then(() -> channel.writeInbound(END))
                .expectNext(END)
                .expectComplete()
                .verify();
        closeNotifyAndVerifyClosing();
    }

    @Test
    public void afterRequestBeforeReadingResponse() {
        sendRequest();
        StepVerifiers.create(conn.read())
                .then(this::closeNotifyAndVerifyClosing)
                .expectError(ClosedChannelException.class)
                .verify();
    }

    @Test
    public void afterRequestWhileReadingResponse() {
        sendRequest();
        StepVerifiers.create(conn.read())
                .then(() -> channel.writeInbound(BEGIN))
                .expectNext(BEGIN)
                .then(this::closeNotifyAndVerifyClosing)
                .expectError(ClosedChannelException.class)
                .verify();
    }

    @Test
    public void whileWritingRequestBeforeReadingResponse() {
        PublisherSource.Processor<String, String> writeSource = newPublisherProcessor();
        StepVerifiers.create(conn.write(fromSource(writeSource)).merge(conn.read()))
                .then(() -> {
                    // Start writing request
                    writeMsg(writeSource, BEGIN);
                    closeNotifyAndVerifyClosing();
                })
                .expectError(ClosedChannelException.class)
                .verify();
    }

    @Test
    public void whileWritingRequestAndReadingResponse() {
        PublisherSource.Processor<String, String> writeSource = newPublisherProcessor();
        StepVerifiers.create(conn.write(fromSource(writeSource)).merge(conn.read()))
                .then(() -> {
                    // Start writing request
                    writeMsg(writeSource, BEGIN);
                    // Start reading response
                    channel.writeInbound(BEGIN);
                })
                .expectNext(BEGIN)
                .then(this::closeNotifyAndVerifyClosing)
                .expectError(ClosedChannelException.class)
                .verify();
    }

    @Test
    public void whileWritingRequestAfterReadingResponse() {
        PublisherSource.Processor<String, String> writeSource = newPublisherProcessor();
        StepVerifiers.create(conn.write(fromSource(writeSource)).merge(conn.read()))
                .then(() -> {
                    // Start writing request
                    writeMsg(writeSource, BEGIN);
                    // Read response
                    channel.writeInbound(BEGIN);
                    channel.writeInbound(END);
                })
                .expectNext(BEGIN, END)
                .then(this::closeNotifyAndVerifyClosing)
                .expectError(ClosedChannelException.class)
                .verify();
    }

    private void sendRequest() {
        PublisherSource.Processor<String, String> writeSource = newPublisherProcessor();
        StepVerifiers.create(conn.write(fromSource(writeSource)))
                .then(() -> {
                    writeMsg(writeSource, BEGIN);
                    writeMsg(writeSource, END);
                })
                .expectComplete()
                .verify();
    }
}

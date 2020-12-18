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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.test.FirstSteps;

import org.junit.Test;

import java.nio.channels.ClosedChannelException;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;

public class SslCloseNotifyAlertServerHandlingTest extends AbstractSslCloseNotifyAlertHandlingTest {

    public SslCloseNotifyAlertServerHandlingTest() throws Exception {
        super(false);
    }

    @Test
    public void afterExchangeIdleConnection() {
        receiveRequest();
        PublisherSource.Processor<String, String> writeSource = newPublisherProcessor();
        FirstSteps.create(conn.write(fromSource(writeSource)))
                .then(() -> {
                    writeMsg(writeSource, BEGIN);
                    writeMsg(writeSource, END);
                    closeNotifyAndVerifyClosing();
                })
                .expectError(ClosedChannelException.class)
                .verify();
    }

    @Test
    public void afterRequestBeforeSendingResponse() {
        receiveRequest();

        PublisherSource.Processor<String, String> writeSource = newPublisherProcessor();
        FirstSteps.create(conn.write(fromSource(writeSource)))
                .then(this::closeNotifyAndVerifyClosing)
                .expectError(ClosedChannelException.class)
                .verify();
    }

    @Test
    public void afterRequestWhileSendingResponse() {
        receiveRequest();

        PublisherSource.Processor<String, String> writeSource = newPublisherProcessor();
        FirstSteps.create(conn.write(fromSource(writeSource)))
                .then(() -> {
                    writeMsg(writeSource, BEGIN);
                    closeNotifyAndVerifyClosing();
                })
                .expectError(ClosedChannelException.class)
                .verify();
    }

    @Test
    public void whileReadingRequestBeforeSendingResponse() {
        FirstSteps.create(conn.write(fromSource(newPublisherProcessor())).merge(conn.read()))
                .then(() -> {
                    // Start reading request
                    channel.writeInbound(BEGIN);
                    closeNotifyAndVerifyClosing();
                })
                .expectNext(BEGIN)
                .expectError(ClosedChannelException.class)
                .verify();
    }

    @Test
    public void whileReadingRequestAndSendingResponse() {
        PublisherSource.Processor<String, String> writeSource = newPublisherProcessor();
        FirstSteps.create(conn.write(fromSource(writeSource)).merge(conn.read()))
                .then(() -> {
                    // Start reading request
                    channel.writeInbound(BEGIN);
                    // Start writing response
                    writeMsg(writeSource, BEGIN);
                })
                .expectNext(BEGIN)
                .then(this::closeNotifyAndVerifyClosing)
                .expectError(ClosedChannelException.class)
                .verify();
    }

    @Test
    public void whileReadingRequestAfterSendingResponse() {
        PublisherSource.Processor<String, String> writeSource = newPublisherProcessor();
        FirstSteps.create(conn.write(fromSource(writeSource)).merge(conn.read()))
                .then(() -> {
                    // Start reading request
                    channel.writeInbound(BEGIN);
                    // Send response
                    writeMsg(writeSource, BEGIN);
                    writeMsg(writeSource, END);
                })
                .expectNext(BEGIN)
                .then(this::closeNotifyAndVerifyClosing)
                .expectError(ClosedChannelException.class)
                .verify();
    }

    private void receiveRequest() {
        FirstSteps.create(conn.read())
                .then(() -> channel.writeInbound(BEGIN))
                .expectNext(BEGIN)
                .then(() -> channel.writeInbound(END))
                .expectNext(END)
                .expectComplete()
                .verify();
    }
}

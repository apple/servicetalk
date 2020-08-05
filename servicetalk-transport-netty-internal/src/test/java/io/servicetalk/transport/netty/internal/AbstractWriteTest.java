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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.CompletableSource;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public abstract class AbstractWriteTest {

    protected EmbeddedChannel channel;
    protected WriteDemandEstimator demandEstimator;
    protected CompletableSource.Subscriber completableSubscriber;
    protected FailingWriteHandler failingWriteHandler;

    @Before
    public void setUp() throws Exception {
        completableSubscriber = mock(CompletableSource.Subscriber.class);
        failingWriteHandler = new FailingWriteHandler();
        channel = new EmbeddedChannel(failingWriteHandler);
        demandEstimator = mock(WriteDemandEstimator.class);
    }

    @After
    public void tearDown() throws Exception {
        channel.finishAndReleaseAll();
        channel.close().await();
    }

    protected void verifyWriteSuccessful(String... items) {
        channel.flushOutbound();
        if (items.length > 0) {
            assertThat("Message not written.", channel.outboundMessages(), contains((String[]) items));
        } else {
            assertThat("Unexpected message(s) written.", channel.outboundMessages(), is(empty()));
        }
    }

    protected void verifyListenerSuccessful() {
        channel.flushOutbound();
        verify(completableSubscriber).onSubscribe(any());
        verify(completableSubscriber).onComplete();
        verifyNoMoreInteractions(completableSubscriber);
    }

    static final class FailingWriteHandler extends ChannelDuplexHandler {
        private volatile boolean failNextWritePromise;
        private volatile boolean throwFromNextWrite;

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
                throws Exception {
            if (throwFromNextWrite) {
                throw DELIBERATE_EXCEPTION;
            }
            if (failNextWritePromise) {
                promise.tryFailure(DELIBERATE_EXCEPTION);
            } else {
                super.write(ctx, msg, promise);
            }
        }

        void throwFromNextWrite() {
            this.throwFromNextWrite = true;
        }

        void failNextWritePromise() {
            this.failNextWritePromise = true;
        }
    }

    static final class WriteInfo {

        private final long writeCapacityBefore;
        private final long writeCapacityAfter;
        private final String msg;

        WriteInfo(long writeCapacityBefore, long writeCapacityAfter, String msg) {
            this.writeCapacityBefore = writeCapacityBefore;
            this.writeCapacityAfter = writeCapacityAfter;
            this.msg = msg;
        }

        long writeCapacityBefore() {
            return writeCapacityBefore;
        }

        long writeCapacityAfter() {
            return writeCapacityAfter;
        }

        String messsage() {
            return msg;
        }
    }
}

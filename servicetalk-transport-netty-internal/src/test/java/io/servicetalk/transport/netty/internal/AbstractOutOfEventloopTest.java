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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public abstract class AbstractOutOfEventloopTest {
    protected Channel channel;
    private EventLoopGroup eventLoopGroup;
    protected BlockingQueue<Integer> pendingFlush;
    protected BlockingQueue<Integer> written;

    @BeforeEach
    public void setUp() throws InterruptedException {
        eventLoopGroup = new DefaultEventLoopGroup(2);
        channel = new LocalChannel();
        eventLoopGroup.next().register(channel).await(1, SECONDS);
        written = new LinkedBlockingQueue<>();
        pendingFlush = new LinkedBlockingQueue<>();
        channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                if (msg instanceof Integer) {
                    pendingFlush.add((Integer) msg);
                    promise.trySuccess();
                } else {
                    ctx.write(msg, promise);
                }
            }

            @Override
            public void flush(ChannelHandlerContext ctx) {
                written.addAll(pendingFlush);
                pendingFlush.clear();
                ctx.flush();
            }
        });
        setup0();
    }

    public abstract void setup0();

    @AfterEach
    public void tearDown() throws Exception {
        written.clear();
        channel.close().await(DEFAULT_TIMEOUT_SECONDS, SECONDS);
        eventLoopGroup.shutdownGracefully().await(DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    void ensureEnqueuedTaskAreRun(EventLoop loop) throws ExecutionException, InterruptedException {
        loop.submit(() -> { }).get(); // Make sure that enqueued flush task is run.
    }

    EventLoop getDifferentEventloopThanChannel() {
        EventLoop next = eventLoopGroup.next();
        assertThat("Next eventloop is the same as channel's eventloop.", next, is(not(channel.eventLoop())));
        return next;
    }
}

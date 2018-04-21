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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AbstractChannelReadHandlerTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Test
    public void testExceptionCaughtNotHitEndOfPipeline() {
        Throwable error = new IllegalStateException();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef2 = new AtomicReference<>();

        EmbeddedChannel channel = new EmbeddedChannel(new AbstractChannelReadHandler<Object>(v -> true, immediate()) {
            @Override
            protected void onPublisherCreation(ChannelHandlerContext ctx, Publisher<Object> newPublisher) {
                newPublisher.subscribe(new Subscriber<Object>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                    }

                    @Override
                    public void onNext(Object o) {
                        errorRef.set(new AssertionError("Should not be called"));
                    }

                    @Override
                    public void onError(Throwable t) {
                        // Only update if not set previously
                        assertTrue(errorRef.compareAndSet(null, t));
                    }

                    @Override
                    public void onComplete() {
                        errorRef.set(new AssertionError("Should not be called"));
                    }
                });
            }
        }, new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                errorRef2.set(cause);
            }
        });

        channel.pipeline().fireExceptionCaught(error);
        assertSame(error, errorRef.get());
        assertNull("exceptionCaught(...) should not be called", errorRef2.get());
        assertFalse(channel.finish());
    }

    @Test
    public void testExceptionWithNoPublisherClosesChannel() {
        AtomicBoolean handlerRemovedCalled = new AtomicBoolean();
        AtomicBoolean channelActive = new AtomicBoolean(true);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        // AbstractChannelReadHandler creates a new publisher when handlerAdded and the channel active is set, so this test
        // carefully manipulates the channel active to false so when AbstractChannelReadHandler checks the state it will
        // not create a publisher, and then later we remove the ChannelInboundHandlerAdapter which will trigger and exception
        // to be thrown and test the desired criteria.
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) {
                        channelActive.set(false);
                    }

                    @Override
                    public void handlerRemoved(ChannelHandlerContext ctx) {
                        channelActive.set(true);
                        handlerRemovedCalled.set(true);
                        throw DELIBERATE_EXCEPTION;
                    }
                },
        new AbstractChannelReadHandler<Object>(v -> true, immediate()) {
            @Override
            protected void onPublisherCreation(ChannelHandlerContext ctx, Publisher<Object> newPublisher) {
                newPublisher.subscribe(new Subscriber<Object>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                    }

                    @Override
                    public void onNext(Object o) {
                        errorRef.set(new AssertionError("Should not be called"));
                    }

                    @Override
                    public void onError(Throwable t) {
                        // Only update if not set previously
                        assertTrue(errorRef.compareAndSet(null, t));
                    }

                    @Override
                    public void onComplete() {
                        errorRef.set(new AssertionError("Should not be called"));
                    }
                });
            }
        }) {
            @Override
            public boolean isOpen() {
                return channelActive.get() && super.isOpen();
            }

            @Override
            public boolean isActive() {
                return channelActive.get() && super.isActive();
            }
        };
        channel.pipeline().remove(ChannelInboundHandlerAdapter.class);
        assertTrue(handlerRemovedCalled.get());
        assertFalse(channel.isActive());
        assertNull(errorRef.get());
    }

    @Test
    public void testAddHandlerBeforeChannelActiveFired() throws Exception {
        TestHandler handler = new TestHandler(false);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        handler.assertChannel(channel);
    }

    @Test
    public void testAddHandlerAfterActive() throws Exception {
        TestHandler handler = new TestHandler(true);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        handler.assertChannel(channel);
    }

    @Test
    public void testChannelInactivePropagated() throws Exception {
        TestHandler handler = new TestHandler(true);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.close();
        assertTrue(handler.inActiveFired.get());
        handler.assertChannel(channel);
    }

    @Test
    public void testChannelReadPropagated() throws Exception {
        TestHandler handler = new TestHandler(true);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.writeInbound(1);
        assertTrue(handler.readFired.get());
        channel.flushInbound();
        assertTrue(handler.readCompleteFired.get());
        assertEquals(1, ((Integer) channel.readInbound()).intValue());
        handler.assertChannel(channel);
    }

    private static final class TestHandler extends ChannelInitializer<Channel> {
        private final AtomicBoolean activeFired = new AtomicBoolean();
        final AtomicBoolean inActiveFired = new AtomicBoolean();
        final AtomicBoolean readFired = new AtomicBoolean();
        final AtomicBoolean readCompleteFired = new AtomicBoolean();
        private final CountDownLatch publisherLatch = new CountDownLatch(1);
        private final ChannelHandler assertHandler = new AbstractChannelReadHandler<Object>(v -> true, immediate()) {
            @Override
            protected void onPublisherCreation(ChannelHandlerContext ctx, Publisher<Object> newPublisher) {
                assertEquals(active, activeFired.get());
                publisherLatch.countDown();
            }
        };
        private final boolean active;

        TestHandler(boolean active) {
            this.active = active;
        }

        @Override
        protected void initChannel(Channel ch) {
            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    activeFired.set(true);
                    ctx.fireChannelActive();
                    if (active) {
                        assertTrue(ctx.channel().isActive());
                        // Add the handler before this handler now as Channel.isActive() is already true
                        ch.pipeline().addFirst(assertHandler);
                    }
                }

                @Override
                public void channelInactive(ChannelHandlerContext ctx) {
                    inActiveFired.set(true);
                    ctx.fireChannelActive();
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    readFired.set(true);
                    ctx.fireChannelRead(msg);
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    readCompleteFired.set(true);
                    ctx.fireChannelReadComplete();
                }
            });
            if (!active) {
                // Directly add the ChannelHandler before a channelActive(...) event was fired.
                assertFalse(activeFired.get());
                ch.pipeline().addLast(assertHandler);
            }
        }

        void assertChannel(EmbeddedChannel channel) throws InterruptedException {
            publisherLatch.await();
            assertFalse(channel.finish());
        }
    }
}

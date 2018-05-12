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
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.client.api.ServiceDiscoverer.Event;
import io.servicetalk.client.servicediscoverer.ServiceDiscovererTestSubscriber;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.transport.netty.NettyIoExecutors.createExecutor;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class DefaultDnsServiceDiscovererTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private static EventLoopAwareNettyIoExecutor nettyIoExecutor;
    private static TestDnsServer dnsServer;
    private DefaultDnsServiceDiscoverer discoverer;

    @BeforeClass
    public static void beforeClass() throws Exception {
        nettyIoExecutor = toEventLoopAwareNettyIoExecutor(createExecutor());
        dnsServer = new TestDnsServer(new HashSet<>(asList("apple.com", "servicetalk.io")));
        dnsServer.start();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        dnsServer.stop();
        awaitIndefinitely(nettyIoExecutor.closeAsync());
    }

    @Before
    public void setup() {
        discoverer = buildServiceDiscoverer(null);
    }

    @After
    public void tearDown() throws Exception {
        awaitIndefinitely(discoverer.closeAsync());
    }

    @Test
    public void testRetry() throws Exception {
        AtomicInteger retryStrategyCalledCount = new AtomicInteger();
        DefaultDnsServiceDiscoverer retryingDiscoverer = buildServiceDiscoverer((retryCount, cause) -> {
            retryStrategyCalledCount.incrementAndGet();
            return retryCount == 1 && cause instanceof UnknownHostException ? completed() : error(cause);
        });

        try {
            awaitIndefinitely(retryingDiscoverer.discover("unknown.com"));
            fail("Unknown host lookup did not fail.");
        } catch (ExecutionException e) {
            assertThat("Unexpected calls to retry strategy.", retryStrategyCalledCount.get(), equalTo(2));
            assertThat("Unexpected exception during DNS lookup.",
                    e.getCause(), instanceOf(UnknownHostException.class));
        } finally {
            awaitIndefinitely(retryingDiscoverer.closeAsync());
        }
    }

    @Test
    public void unknownHostDiscover() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<Event<InetAddress>> publisher = discoverer.discover("unknown.com");
        publisher.subscribe(new Subscriber<Event<InetAddress>>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(Event<InetAddress> inetAddressEvent) {
                throwableRef.set(new IllegalStateException("unexpected resolution: " + inetAddressEvent));
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                latch.countDown();
            }

            @Override
            public void onComplete() {
                throwableRef.set(new IllegalStateException("unexpected onComplete"));
                latch.countDown();
            }
        });

        latch.await();
        assertNull(throwableRef.get());
    }

    @Test
    public void singleDiscover() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<Event<InetAddress>> publisher = discoverer.discover("apple.com");
        ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, 1);
        publisher.subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.getActiveCount(), greaterThanOrEqualTo(1));
        assertThat(subscriber.getInActiveCount(), greaterThanOrEqualTo(0));
    }

    @Test
    public void repeatDiscover() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<Event<InetAddress>> publisher = discoverer.discover("apple.com");
        ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
        publisher.subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.getActiveCount(), greaterThanOrEqualTo(2));
        assertThat(subscriber.getInActiveCount(), greaterThanOrEqualTo(1));
    }

    @Test
    public void repeatDiscoverMultipleHosts() throws InterruptedException {
        DefaultDnsServiceDiscoverer discoverer = buildServiceDiscoverer(null);

        CountDownLatch appleLatch = new CountDownLatch(2);
        CountDownLatch stLatch = new CountDownLatch(2);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<Event<InetAddress>> applePublisher = discoverer.discover("apple.com");
        Publisher<Event<InetAddress>> stPublisher = discoverer.discover("servicetalk.io");
        ServiceDiscovererTestSubscriber<InetAddress> appleSubscriber =
                new ServiceDiscovererTestSubscriber<>(appleLatch, throwableRef, Long.MAX_VALUE);
        ServiceDiscovererTestSubscriber<InetAddress> stSubscriber =
                new ServiceDiscovererTestSubscriber<>(stLatch, throwableRef, Long.MAX_VALUE);
        applePublisher.subscribe(appleSubscriber);
        stPublisher.subscribe(stSubscriber);

        appleLatch.await();
        stLatch.await();
        assertNull(throwableRef.get());
        assertThat(appleSubscriber.getActiveCount(), greaterThanOrEqualTo(2));
        assertThat(appleSubscriber.getInActiveCount(), greaterThanOrEqualTo(1));
        assertThat(stSubscriber.getActiveCount(), greaterThanOrEqualTo(2));
        assertThat(stSubscriber.getInActiveCount(), greaterThanOrEqualTo(1));
    }

    @SuppressWarnings("unchecked")
    @Test(expected = ExecutionException.class)
    public void exceptionInSubscriberOnErrorWhileClose() throws Exception {
        CountDownLatch latchOnSubscribe = new CountDownLatch(1);
        DefaultDnsServiceDiscoverer discoverer = buildServiceDiscoverer(null);
        Subscriber<Event<InetAddress>> subscriber = mock(Subscriber.class);

        try {
            doAnswer(a -> {
                Subscription s = a.getArgument(0);
                s.request(1);
                latchOnSubscribe.countDown();
                return null;
            }).when(subscriber).onSubscribe(any(Subscription.class));
            doThrow(DELIBERATE_EXCEPTION).when(subscriber).onError(any());

            discoverer.discover("apple.com").subscribe(subscriber);
            latchOnSubscribe.await();
        } finally {
            awaitIndefinitely(discoverer.closeAsync());
        }
    }

    private static DefaultDnsServiceDiscoverer buildServiceDiscoverer(
            @Nullable BiIntFunction<Throwable, Completable> retryStrategy) {

        DefaultDnsServiceDiscoverer.Builder builder =
                new DefaultDnsServiceDiscoverer.Builder(nettyIoExecutor, immediate())
                .setDnsResolverAddressTypes(DnsResolverAddressTypes.IPV4_ONLY)
                .setOptResourceEnabled(false)
                .setDnsServerAddressStreamProvider(new SingletonDnsServerAddressStreamProvider(
                        new SingletonDnsServerAddresses(dnsServer.localAddress())))
                .setNdots(1);

        if (retryStrategy != null) {
            builder.retryDnsFailures(retryStrategy);
        }
        return builder.build();
    }
}

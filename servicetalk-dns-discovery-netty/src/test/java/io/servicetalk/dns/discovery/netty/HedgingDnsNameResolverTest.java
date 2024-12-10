package io.servicetalk.dns.discovery.netty;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HedgingDnsNameResolverTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(HedgingDnsNameResolverTest.class);

    @RegisterExtension
    static final ExecutorExtension<TestExecutor> timerExecutor = ExecutorExtension.withTestExecutor()
            .setClassLevel(true);

    @RegisterExtension
    static final ExecutorExtension<EventLoopAwareNettyIoExecutor> ioExecutor = ExecutorExtension
            .withExecutor(() -> createIoExecutor(1))
            .setClassLevel(true);

    HedgingDnsNameResolver.PercentileTracker percentileTracker;
    HedgingDnsNameResolver.Budget budget;

    UnderlyingDnsResolver underlying;
    HedgingDnsNameResolver resolver;

    void setup() {
        if (percentileTracker == null) {
            percentileTracker = mock(HedgingDnsNameResolver.PercentileTracker.class);
            when(percentileTracker.getValue()).thenReturn(10L);
        }
        if (budget == null) {
            budget = mock(HedgingDnsNameResolver.Budget.class);
            when(budget.withdraw()).thenReturn(true);
        }
        underlying = mock(UnderlyingDnsResolver.class);
        // DnsResolverIface delegate, Executor executor, EventLoop eventLoop,
        //                           PercentileTracker percentile, Budget budget
        resolver = new HedgingDnsNameResolver(underlying,
                new DefaultDnsClientTest.NettyIoExecutorWithTestTimer(ioExecutor.executor(), timerExecutor.executor()),
                percentileTracker, budget);
    }

    @Test
    void requestThatDoesntNeedHedge() throws Exception {
        setup();
        Promise<List<InetAddress>> p1 = newPromise();
        when(underlying.resolveAll(any())).thenReturn(p1, null);
        Future<List<InetAddress>> results = resolver.resolveAll("apple.com");
        assertThat(results.isDone(), equalTo(false));
        advanceTime(1);
        List<InetAddress> result = new ArrayList<>();
        p1.trySuccess(result);
        assertThat(results.get(), equalTo(result));
        verify(budget).deposit();
        verify(budget, never()).withdraw();
        verify(percentileTracker).addSample(1);
    }

    @Test
    void requestWithHedgingAndFirstWins() throws Exception {
        setup();
        Promise<List<InetAddress>> p1 = newPromise();
        Promise<List<InetAddress>> p2 = newPromise();
        when(underlying.resolveAll(any())).thenReturn(p1, p2);
        Future<List<InetAddress>> results = resolver.resolveAll("apple.com");
        assertThat(results.isDone(), equalTo(false));
        advanceTime(10);
        List<InetAddress> result = new ArrayList<>();
        p1.trySuccess(result);
        assertThat(results.get(), equalTo(result));
        verify(budget).deposit();
        verify(budget, times(1)).withdraw();
        verify(percentileTracker).addSample(10);
        assertThat(p2.isCancelled(), equalTo(true));
    }

    @Test
    void requestWithHedgingAndSecondWins() throws Exception {
        setup();
        Promise<List<InetAddress>> p1 = newPromise();
        Promise<List<InetAddress>> p2 = newPromise();
        when(underlying.resolveAll(any())).thenReturn(p1, p2);

        Future<List<InetAddress>> results = resolver.resolveAll("apple.com");
        assertThat(results.isDone(), equalTo(false));
        advanceTime(10);

        // Hedging should have started and the new timer should be set.
        advanceTime(5);
        List<InetAddress> result = new ArrayList<>();
        p2.trySuccess(result);
        assertThat(results.get(), equalTo(result));
        verify(budget).deposit();
        verify(budget, times(1)).withdraw();
        verify(percentileTracker).addSample(5); // only add the successful sample.
        assertThat(p1.isCancelled(), equalTo(true));
    }

    @Test
    void requestWhenNoHedgingBudget() throws Exception {
        setup();
        when(budget.withdraw()).thenReturn(false);
        Promise<List<InetAddress>> p1 = newPromise();
        when(underlying.resolveAll(any())).thenReturn(p1);

        Future<List<InetAddress>> results = resolver.resolveAll("apple.com");
        assertThat(results.isDone(), equalTo(false));
        advanceTime(10);

        verify(budget).deposit();
        verify(budget, times(1)).withdraw();
        verify(underlying, times(1)).resolveAll("apple.com");
    }

    private <V> Promise<V> newPromise() {
        return ioExecutor.executor().eventLoopGroup().next().newPromise();
    }

    private static void advanceTime(int advance) throws Exception {
        // To make sure that the time is advanced after all prior work on the EvenLoop is complete, we advance it from
        // the EventLoop too.
        ioExecutor.executor().submit(() -> {
            LOGGER.debug("Advance time by {}s.", advance);
            timerExecutor.executor().advanceTimeBy(advance, MILLISECONDS);
        }).toFuture().get();
    }
}

/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.loadbalancer.DynamicApertureLoadBalancer;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.AddressUtils;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class DynamicApertureLoadBalancerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicApertureLoadBalancerTest.class);

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private static ServerContext srv;
    private static BlockingHttpClient client;
    private static final CompositeCloseable cc = AsyncCloseables.newCompositeCloseable();

    @BeforeClass
    public static void setup() throws Exception {
        cc.prepend(srv = HttpServers.forAddress(AddressUtils.localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    LOGGER.trace("Request From: {}", ctx.remoteAddress());
                    return responseFactory.ok();
                }));

        cc.prepend((client = HttpClients.forSingleAddress(AddressUtils.serverHostAndPort(srv))
                .loadBalancerFactory(
                        DynamicApertureLoadBalancer::new,
                        StaticScoreHttpProtocolBinder.provideStaticScoreIfNeeded(1f))
                .buildBlocking()).asClient());
    }

    @AfterClass
    public static void teardown() throws Exception {
        cc.close();
    }

    @Test
    public void simpleConcurrentTest() throws Exception {
        ExecutorService exec = null;
        try {
            int nThreads = Runtime.getRuntime().availableProcessors();
            exec = Executors.newFixedThreadPool(nThreads);
            List<Callable<Integer>> tasks = IntStream.range(0, 2 * nThreads)
                    .mapToObj(__ ->
                            (Callable<Integer>) () -> {
                                HttpResponse response = client.request(client.get("/test"));
                                return response.status().code();
                            })
                    .collect(Collectors.toList());
            List<Future<Integer>> futures = exec.invokeAll(tasks);
            for (Future<Integer> future : futures) {
                assertThat(future.get(), equalTo(OK.code()));
            }
        } finally {
            if (exec != null) {
                exec.shutdownNow();
                exec.awaitTermination(2, SECONDS);
            }
        }
    }
}

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
package io.servicetalk.http.all.netty;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.AggregatedHttpRequester;
import io.servicetalk.http.api.BlockingAggregatedHttpRequester;
import io.servicetalk.http.api.BlockingHttpRequester;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class AddressParsingHttpRequesterBuilderTest {

    @ClassRule
    public static final ExecutionContextRule CTX = immediate();

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Test
    public void buildWithDefaults() throws Exception {
        HttpRequester newRequester = new AddressParsingHttpRequesterBuilder()
                .build(CTX);
        assertNotNull(newRequester);
        awaitIndefinitely(newRequester.closeAsync());
    }

    @Test
    public void buildAggregatedWithDefaults() throws Exception {
        AggregatedHttpRequester newAggregatedRequester = new AddressParsingHttpRequesterBuilder()
                .buildAggregated(CTX);
        assertNotNull(newAggregatedRequester);
        awaitIndefinitely(newAggregatedRequester.closeAsync());
    }

    @Test
    public void buildBlockingWithDefaults() throws Exception {
        BlockingHttpRequester newBlockingRequester = new AddressParsingHttpRequesterBuilder()
                .buildBlocking(CTX);
        assertNotNull(newBlockingRequester);
        newBlockingRequester.close();
    }

    @Test
    public void buildBlockingAggregatedWithDefaults() throws Exception {
        BlockingAggregatedHttpRequester newBlockingAggregatedRequester = new AddressParsingHttpRequesterBuilder()
                .buildBlockingAggregated(CTX);
        assertNotNull(newBlockingAggregatedRequester);
        newBlockingAggregatedRequester.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void buildWithProvidedServiceDiscoverer() throws Exception {
        ServiceDiscoverer<HostAndPort, InetSocketAddress> mockedServiceDiscoverer = mock(ServiceDiscoverer.class);
        HttpRequester newRequester = new AddressParsingHttpRequesterBuilder(mockedServiceDiscoverer).build(CTX);
        awaitIndefinitely(newRequester.closeAsync());
        verify(mockedServiceDiscoverer, never()).closeAsync();
    }
}

/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;

import org.junit.jupiter.api.Test;

import static io.servicetalk.transport.api.ConnectionAcceptor.ACCEPT_ALL;
import static io.servicetalk.transport.api.ConnectionAcceptorFactory.withStrategy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class InfluencerConnectionAcceptorTest {

    @Test
    void defaultRequiredOffloads() {
        ConnectionAcceptorFactory factory = ConnectionAcceptorFactory.identity();
        assertThat("Default should be safe", factory.requiredOffloads().hasOffloads());

        InfluencerConnectionAcceptor acceptor =
                InfluencerConnectionAcceptor.withStrategy(factory.create(ACCEPT_ALL), factory.requiredOffloads());
        assertThat("acceptor should have same strategy", acceptor.requiredOffloads(), is(factory.requiredOffloads()));
    }

    @Test
    void withStrategyRequiredOffloads() {
        ConnectionAcceptorFactory factory = withStrategy(original -> original, ConnectExecutionStrategy.offloadAll());
        assertThat("unexpected strategy", factory.requiredOffloads(), is(ConnectExecutionStrategy.offloadAll()));

        InfluencerConnectionAcceptor acceptor =
                InfluencerConnectionAcceptor.withStrategy(factory.create(ACCEPT_ALL), factory.requiredOffloads());
        assertThat("acceptor should have same strategy", acceptor.requiredOffloads(), is(factory.requiredOffloads()));
    }
}

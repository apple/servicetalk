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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.ConnectionContext;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class ConnectionAcceptorFilterTest {

    @Test
    void testAppend() throws Exception {
        Deque<Integer> createOrder = new ArrayDeque<>();
        Deque<Integer> connectOrder = new ArrayDeque<>();
        class AcceptorOrder implements ConnectionAcceptor {
            final int order;
            ConnectionAcceptor original;
            AcceptorOrder(int order, ConnectionAcceptor original) {
                this.order = order;
                this.original = original;
            }
            @Override
            public Completable accept(ConnectionContext context) {
                connectOrder.add(order);
                return original.accept(context);
            }

            @Override
            public Completable closeAsync() {
                return Completable.completed();
            }
        }

        class FilterOrder implements ConnectionAcceptorFactory {

            final int order;
            FilterOrder(int order) {
                this.order = order;
            }

            @Override
            public ConnectionAcceptor create(ConnectionAcceptor original) {
                createOrder.add(order);
                return new AcceptorOrder(order, original);
            }
        };

        FilterOrder first = new FilterOrder(1);
        FilterOrder second = new FilterOrder(2);

        ConnectionAcceptorFactory combined = first.append(second);

        ConnectionAcceptor acceptor = combined.create(ConnectionAcceptor.ACCEPT_ALL);

        Void nothing = acceptor.accept(null).toFuture().get();

        assertThat(createOrder, is(hasSize(2)));
        assertThat(createOrder, is(containsInRelativeOrder(2, 1)));
        assertThat(connectOrder, is(hasSize(2)));
        assertThat(connectOrder, is(containsInRelativeOrder(1, 2)));
    }
}

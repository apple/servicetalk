/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.client.api.ScoreSupplier;
import io.servicetalk.http.api.HttpLoadBalancerFactory.DefaultFilterableStreamingHttpLoadBalancedConnection;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class HttpLoadBalancerFactoryTest {

    @Test
    void scoresUsingTheConcurrencyControllerWhenItSupportsScoring() {
        ReservableRequestConcurrencyController controller = mock(ReservableRequestConcurrencyController.class,
                withSettings().extraInterfaces(ScoreSupplier.class));
        when(((ScoreSupplier) controller).score()).thenReturn(-3);
        assertThat(newConnection(controller).score(), is(-3));
    }

    @Test
    void scoresEquallyWhenTheConcurrencyControllerCanNotScore() {
        assertThat(newConnection(mock(ReservableRequestConcurrencyController.class)).score(), is(0));
    }

    private static DefaultFilterableStreamingHttpLoadBalancedConnection newConnection(
            ReservableRequestConcurrencyController controller) {
        return new DefaultFilterableStreamingHttpLoadBalancedConnection(
                mock(FilterableStreamingHttpConnection.class), controller);
    }
}

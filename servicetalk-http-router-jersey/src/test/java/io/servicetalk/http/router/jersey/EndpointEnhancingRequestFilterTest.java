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
package io.servicetalk.http.router.jersey;

import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.internal.util.collection.Refs;
import org.glassfish.jersey.process.internal.RequestScope;
import org.junit.jupiter.api.Test;

import javax.inject.Provider;
import javax.ws.rs.container.ContainerRequestContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

class EndpointEnhancingRequestFilterTest {

    @Test
    void shouldNotFilterNonStRequests() throws Exception {
        EndpointEnhancingRequestFilter filter = new EndpointEnhancingRequestFilter();
        Provider<Ref<ConnectionContext>> ctxRefProvider = () -> Refs.of(null);
        Provider<RouteStrategiesConfig> routeStrategiesConfigProvider = () -> mock(RouteStrategiesConfig.class);
        RequestScope requestScope = mock(RequestScope.class);
        setField(filter, filter.getClass().getDeclaredField("ctxRefProvider"), ctxRefProvider);
        setField(filter, filter.getClass().getDeclaredField("routeStrategiesConfigProvider"),
                routeStrategiesConfigProvider);
        setField(filter, filter.getClass().getDeclaredField("requestScope"), requestScope);

        ContainerRequestContext context = mock(ContainerRequestContext.class);
        filter.filter(mock(ContainerRequestContext.class));
        verifyZeroInteractions(context);
    }
}

/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry.grpc;

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.transport.api.ConnectionInfo;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestHeadersPropagatorSetterTest {
    @Test
    void shouldCallConsumerWhenCarrierIsNotNull() {

        final AtomicInteger nCalls = new AtomicInteger(0);
        HttpHeaders carrier = mock(HttpHeaders.class);

        when(carrier.set(anyString(), anyString())).thenAnswer(a -> {
            String k = a.getArgument(0);
            String v = a.getArgument(1);
            nCalls.incrementAndGet();

            assertThat(k).isEqualTo("k");
            assertThat(v).isEqualTo("v");
            return carrier;
        });

        final TextMapSetter<GrpcRequestInfo> setter = RequestHeadersPropagatorSetter.INSTANCE;
        HttpRequestMetaData responseMetaData = mock(HttpRequestMetaData.class);
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        GrpcRequestInfo grpcRequestInfo = new GrpcRequestInfo(responseMetaData, connectionInfo);
        when(responseMetaData.headers()).thenReturn(carrier);

        setter.set(grpcRequestInfo, "k", "v");

        assertThat(nCalls.get()).isEqualTo(1);
    }

    @Test
    void shouldNotThrowWhenCarrierIsNull() {
        assertThatNoException().isThrownBy(() -> RequestHeadersPropagatorSetter.INSTANCE.set(null, "k", "v"));
    }
}

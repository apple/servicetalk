/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.grpc.api.GrpcClientMetadata;

import io.grpc.examples.helloworld.Greeter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcInputValidationTest {

    @Test
    void verifyClientEarlyNonNullArgumentsCheck() throws Exception {
        try (Greeter.BlockingGreeterClient client = GrpcClients
                .forAddress("localhost", 0)
                .buildBlocking(new Greeter.ClientFactory())) {

            assertEarlyRequireNonNull(() -> client.sayHello(null));
            assertEarlyRequireNonNull(() -> client.sayHello((GrpcClientMetadata) null, null));
        }
    }

    @Test
    void verifyServiceEarlyNonNullArgumentsCheck() {
        assertEarlyRequireNonNull(() -> GrpcServers
                .forAddress(localAddress(0))
                .listenAndAwait(new Greeter.ServiceFactory.Builder().sayHello(null).build()));

        assertEarlyRequireNonNull(() -> GrpcServers
                .forAddress(localAddress(0))
                .listenAndAwait(new Greeter.ServiceFactory.Builder().sayHello(null, null).build()));
    }

    private static void assertEarlyRequireNonNull(final Executable executable) {
        NullPointerException ex = assertThrows(NullPointerException.class, executable);
        assertEquals("requireNonNull", ex.getStackTrace()[0].getMethodName());
    }
}

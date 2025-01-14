/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.http.netty.SpliceFlatStreamToMetaSingle;
import io.servicetalk.leak.LeakMessage;
import io.servicetalk.leak.Leaker;
import io.servicetalk.transport.api.HostAndPort;

import io.netty.buffer.ByteBufUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.api.internal.BlockingUtils.blockingInvocation;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class GcWatchdogLeakDetectorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GcWatchdogLeakDetectorTest.class);

    private static boolean leakDetected;

    static {
        System.setProperty("io.servicetalk.http.netty.leakdetection", "strict");
        System.setProperty("io.netty.leakDetection.level", "paranoid");
        ByteBufUtil.setLeakListener((type, records) -> {
            leakDetected = true;
            LOGGER.error("ByteBuf leak detected!");
        });
    }

    @Test
    void testLeak() throws Exception {
        GrpcServers.forPort(8888)
                .listenAndAwait(new Leaker.LeakerService() {
                    @Override
                    public Publisher<LeakMessage> rpc(GrpcServiceContext ctx, Publisher<LeakMessage> request) {
                        Publisher<LeakMessage> response = splice(request)
                                .flatMapPublisher(pair -> Publisher.failed(
                                            new GrpcStatusException(GrpcStatusCode.INVALID_ARGUMENT.status())));
                        return response;
                    }
                });

        Leaker.LeakerClient client = GrpcClients.forAddress(HostAndPort.of("localhost", 8888))
                .build(new Leaker.ClientFactory());

        for (int i = 0; i < 10; i++) {
            blockingInvocation(
                    client.rpc(
                            Publisher.from(
                                    LeakMessage.newBuilder().setValue("first LeakMessage").build(),
                                    LeakMessage.newBuilder().setValue("second LeakMessage (which leaks)").build()))
                    .ignoreElements()
                    .onErrorComplete());

            System.gc();
            System.runFinalization();
        }

        assertFalse(leakDetected);
    }

    private static Single<Pair> splice(Publisher<LeakMessage> request) {
        return request.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Pair::new));
    }

    private static final class Pair {
        final LeakMessage head;
        final Publisher<LeakMessage> stream;

        Pair(LeakMessage head, Publisher<LeakMessage> stream) {
            this.head = head;
            this.stream = stream;
        }
    }
}

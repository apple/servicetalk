/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.protoc;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.protoc.test.multi.TestReply;
import io.servicetalk.grpc.protoc.test.multi.TestRequest;
import io.servicetalk.grpc.protoc.test.multi.Tester.TesterService;
import io.servicetalk.grpc.protoc.test.multi.Tester2.Tester2Service;

import org.junit.jupiter.api.Test;
import test.shared.TestShared.Generic;
import test.shared.TestShared.SharedReply;
import test.shared.TestShared.Untils;

import java.util.concurrent.ExecutionException;

class TestMulti {
    @Test
    void multiGenerated() throws ExecutionException, InterruptedException {
        TesterService service = new TesterService() {
            @Override
            public Publisher<TestReply> testBiDiStream(final GrpcServiceContext ctx,
                                                       final Publisher<TestRequest> request) {
                return Publisher.from(TestReply.newBuilder().build());
            }

            @Override
            public Single<TestReply> testRequestStream(final GrpcServiceContext ctx,
                                                       final Publisher<TestRequest> request) {
                return Single.succeeded(TestReply.newBuilder().build());
            }

            @Override
            public Publisher<TestReply> testResponseStream(final GrpcServiceContext ctx, final TestRequest request) {
                return Publisher.from(TestReply.newBuilder().build());
            }

            @Override
            public Single<TestReply> test(final GrpcServiceContext ctx, final TestRequest request) {
                return Single.succeeded(TestReply.newBuilder().build());
            }
        };

        Tester2Service service2 = new Tester2Service() {
            @Override
            public Publisher<Untils> testUntils(final GrpcServiceContext ctx,
                                                final Generic.Empty request) {
                return Publisher.from(Untils.newBuilder().build());
            }

            @Override
            public Single<SharedReply> testShared(final GrpcServiceContext ctx, final TestRequest request) {
                return Single.succeeded(SharedReply.newBuilder().build());
            }
        };

        service.closeAsync().toFuture().get();
        service2.closeAsync().toFuture().get();
    }
}

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
import io.servicetalk.grpc.protoc.test.single.HelloWorldProto.Fareweller.FarewellerService;
import io.servicetalk.grpc.protoc.test.single.HelloWorldProto.Greeter.GreeterService;
import io.servicetalk.grpc.protoc.test.single.HelloWorldProto.HelloReply;
import io.servicetalk.grpc.protoc.test.single.HelloWorldProto.UserRequest;

import org.junit.jupiter.api.Test;
import test.shared.TestShared.Generic;
import test.shared.TestShared.SharedReply;
import test.shared.TestShared.Untils;

import java.util.concurrent.ExecutionException;

class TestSingle {
    @Test
    void singleGenerated() throws ExecutionException, InterruptedException {
        GreeterService service = new GreeterService() {
            @Override
            public Single<HelloReply> sayHelloFromMany(final GrpcServiceContext ctx,
                                                       final Publisher<UserRequest> request) {
                return Single.succeeded(HelloReply.newBuilder().build());
            }

            @Override
            public Single<HelloReply> sayHello(final GrpcServiceContext ctx, final UserRequest request) {
                return Single.succeeded(HelloReply.newBuilder().build());
            }

            @Override
            public Publisher<HelloReply> sayHelloToFromMany(final GrpcServiceContext ctx,
                                                            final Publisher<UserRequest> request) {
                return Publisher.from(HelloReply.newBuilder().build());
            }

            @Override
            public Publisher<HelloReply> sayHelloToMany(final GrpcServiceContext ctx, final UserRequest request) {
                return Publisher.from(HelloReply.newBuilder().build());
            }
        };

        FarewellerService service2 = new FarewellerService() {
            @Override
            public Publisher<Untils> getAllUntils(final GrpcServiceContext ctx, final Generic.Empty request) {
                return Publisher.from(Untils.newBuilder().build());
            }

            @Override
            public Single<SharedReply> sayGoodbye(final GrpcServiceContext ctx, final UserRequest request) {
                return Single.succeeded(SharedReply.newBuilder().build());
            }
        };

        service.closeAsync().toFuture().get();
        service2.closeAsync().toFuture().get();
    }
}

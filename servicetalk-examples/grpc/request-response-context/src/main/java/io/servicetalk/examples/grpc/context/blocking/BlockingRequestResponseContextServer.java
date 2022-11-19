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
package io.servicetalk.examples.grpc.context.blocking;

import io.servicetalk.examples.grpc.context.HttpContextFilters;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.GrpcServers;
import io.servicetalk.http.api.HttpMetaData;

import io.grpc.examples.helloworld.Greeter.BlockingGreeterService;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;

/**
 * This example demonstrates how {@link GrpcServiceContext#requestContext() request} and
 * {@link GrpcServiceContext#responseContext() response} context can be used to access {@link HttpMetaData}.
 * <p>
 * Start this server first and then run the {@link BlockingRequestResponseContextClient}.
 */
public final class BlockingRequestResponseContextServer {
    public static void main(String[] args) throws Exception {
        GrpcServers.forPort(8080)
                .initializeHttp(httpBuilder -> httpBuilder.appendServiceFilter(HttpContextFilters.serviceFilter()))
                .listenAndAwait(new BlockingGreeterService() {
                    @Override
                    public HelloReply sayHello(final GrpcServiceContext ctx, final HelloRequest request) {
                        CharSequence userId = ctx.requestContext().get(HttpContextFilters.USER_ID_KEY);
                        ctx.responseContext().put(HttpContextFilters.USER_ID_KEY, userId + "-processed");
                        return HelloReply.newBuilder().setMessage("Hello " + request.getName()).build();
                    }
                })
                .awaitShutdown();
    }
}

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
package io.servicetalk.examples.grpc.helloworld;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.GreeterService;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.GreeterServiceFilter;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.GreeterServiceFilterFactory;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.ServiceFactory;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.HelloReply;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.HelloRequest;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.GrpcServers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Single.succeeded;

public class ServiceFilters {

    public static void main(String[] args) throws Exception {
        GrpcServers.forPort(8080)
                .listenAndAwait(new ServiceFactory(new MyGreeterService())
                        .appendServiceFilter(new MyServiceFilter()))
                .awaitShutdown();
    }

    private static final class MyServiceFilter implements GreeterServiceFilterFactory {
        private static final Logger LOGGER = LoggerFactory.getLogger(MyServiceFilter.class);

        @Override
        public GreeterServiceFilter create(final GreeterService greeterService) {
            return new GreeterServiceFilter(greeterService) {
                @Override
                public Single<HelloReply> sayHello(final GrpcServiceContext ctx, final HelloRequest request) {
                    LOGGER.error("New request to say hello for {}.", request.getName());
                    return delegate().sayHello(ctx, request);
                }

                @Override
                public Publisher<HelloReply> sayHelloToFromMany(final GrpcServiceContext ctx, final Publisher<HelloRequest> request) {
                    LOGGER.error("New request to say hello streaming.");
                    return delegate().sayHelloToFromMany(ctx, request);
                }

                @Override
                public Publisher<HelloReply> sayHelloToMany(final GrpcServiceContext ctx, final HelloRequest request) {
                    LOGGER.error("New request to say hello response streaming.");
                    return delegate().sayHelloToMany(ctx, request);
                }

                @Override
                public Single<HelloReply> sayHelloFromMany(final GrpcServiceContext ctx, final Publisher<HelloRequest> request) {
                    LOGGER.error("New request to say hello request streaming.");
                    return delegate().sayHelloFromMany(ctx, request);
                }
            };
        }
    }

    private static final class MyGreeterService implements GreeterService {

        @Override
        public Single<HelloReply> sayHello(final GrpcServiceContext ctx, final HelloRequest request) {
            return succeeded(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        }

        @Override
        public Publisher<HelloReply> sayHelloToFromMany(final GrpcServiceContext ctx,
                                                        final Publisher<HelloRequest> request) {
            return request.map(req -> HelloReply.newBuilder().setMessage("Hello " + req.getName()).build());
        }

        @Override
        public Publisher<HelloReply> sayHelloToMany(final GrpcServiceContext ctx, final HelloRequest request) {
            return ctx.executionContext().executor()
                    .timer(Duration.ofMillis(100))
                    .repeat(count -> count < 10)
                    .map(new Function<Void, HelloReply>() {

                        private int count;

                        @Override
                        public HelloReply apply(final Void __) {
                            return HelloReply.newBuilder().setMessage(request.getName() + ", Hello " + ++count + ": ")
                                    .build();
                        }
                    });
        }

        @Override
        public Single<HelloReply> sayHelloFromMany(final GrpcServiceContext ctx,
                                                   final Publisher<HelloRequest> request) {
            return request.collect(StringBuilder::new, (names, req) -> {
                if (names.length() != 0) {
                    names.append(",");
                }
                names.append(req.getName());
                return names;
            }).map(names -> HelloReply.newBuilder().setMessage("Hello " + names.toString()).build());
        }
    }
}

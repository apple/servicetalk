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
package io.servicetalk.examples.grpc.compression;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.encoding.netty.ContentCodings;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.GrpcServers;

import io.grpc.examples.compression.Greeter.GreeterService;
import io.grpc.examples.compression.Greeter.ServiceFactory;
import io.grpc.examples.compression.HelloReply;
import io.grpc.examples.compression.HelloRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.servicetalk.concurrent.api.Single.succeeded;

/**
 * A simple extension of the gRPC "Hello World" example which demonstrates
 * compression of the request and response bodies.
 */
public class CompressionExampleServer {

    /**
     * Supported encodings in preferred order. These will be matched against the list of encodings provided by the
     * client to choose a mutually agreeable encoding.
     */
    private static final List<ContentCodec> SUPPORTED_ENCODINGS =
            Collections.unmodifiableList(Arrays.asList(
                    ContentCodings.gzipDefault(),
                    ContentCodings.deflateDefault(),
                    ContentCodings.identity()
            ));

    public static void main(String... args) throws Exception {
        GrpcServers.forPort(8080)
                // Create Greeter service which uses default binding to create ServiceFactory.
                // (see {@link MyGreeterService#bindService}). Alternately a non-default binding could be used by
                // directly creating a ServiceFactory directly. i.e.
                //    new ServiceFactory(new MyGreeterService(), strategyFactory, contentCodecs)
                .listenAndAwait(new MyGreeterService())
                .awaitShutdown();
    }

    private static final class MyGreeterService implements GreeterService {

        @Override
        public Single<HelloReply> sayHello(final GrpcServiceContext ctx, final HelloRequest request) {
            return succeeded(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        }

        @Override
        public ServiceFactory bindService() {
            // Create a ServiceFactory bound to this service and includes the encodings supported for requests and
            // the preferred encodings for responses. Responses will automatically be compressed if the request includes
            // a mutually agreeable compression encoding that the client indicates they will accept and that the
            // server supports. Requests using unsupported encodings receive an error response in the "grpc-status".
            return new ServiceFactory(this, SUPPORTED_ENCODINGS);
        }
    }
}

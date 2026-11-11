/*
 * Copyright © 2021-2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.errors;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.GrpcServers;

import com.google.rpc.BadRequest;
import com.google.rpc.BadRequest.FieldViolation;
import com.google.rpc.Status;
import io.grpc.examples.errors.Greeter.GreeterService;
import io.grpc.examples.errors.HelloReply;
import io.grpc.examples.errors.HelloRequest;

import static com.google.protobuf.Any.pack;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcStatusCode.INVALID_ARGUMENT;

/**
 * Extends the async "Hello World" example to include support for application error propagation.
 */
public final class ErrorExampleServer {
    public static void main(String... args) throws Exception {
        // GrpcExceptionMapperServiceFilter is installed automatically to handle errors from other filters.
        GrpcServers.forPort(8080)
                .listenAndAwait(new ErrorGreeterService())
                .awaitShutdown();
    }

    private static final class ErrorGreeterService implements GreeterService {
        @Override
        public Single<HelloReply> sayHello(final GrpcServiceContext ctx, final HelloRequest request) {
            return defer(() -> {
                if (request.getToken().isEmpty()) {
                    throw new InvalidFieldException(
                            HelloRequest.getDescriptor().findFieldByNumber(HelloRequest.TOKEN_FIELD_NUMBER)
                                    .getFullName(),
                            "missing required field!");
                }
                return succeeded(HelloReply.newBuilder().setMessage("hello " + request.getName()).build());
            }).onErrorMap(InvalidFieldException.class, ErrorExampleServer::mapException);
        }
    }

    private static GrpcStatusException mapException(final InvalidFieldException cause) {
        return GrpcStatusException.of(Status.newBuilder()
                .setCode(INVALID_ARGUMENT.value())
                .setMessage("example message for invalid argument")
                .addDetails(pack(BadRequest.newBuilder().addFieldViolations(
                        FieldViolation.newBuilder()
                                .setField(cause.field)
                                .setDescription(cause.getMessage())
                                .build())
                        .build()))
                .build());
    }

    /**
     * An application exception containing only validation details that are safe to expose to clients.
     */
    private static final class InvalidFieldException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String field;

        InvalidFieldException(final String field, final String description) {
            super(description);
            this.field = field;
        }
    }
}

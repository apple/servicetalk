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
package io.servicetalk.grpc.protoc;

import com.squareup.javapoet.ClassName;

import static com.squareup.javapoet.ClassName.bestGuess;

final class Types {
    private static final String basePkg = "io.servicetalk";
    private static final String concurrentPkg = basePkg + ".concurrent";
    private static final String concurrentApiPkg = basePkg + ".concurrent.api";
    private static final String grpcBasePkg = basePkg + ".grpc";
    private static final String grpcApiPkg = grpcBasePkg + ".api";
    private static final String grpcRoutesFqcn = grpcApiPkg + ".GrpcRoutes";
    private static final String grpcProtobufPkg = grpcBasePkg + ".protobuf";

    static final ClassName BlockingIterable = bestGuess(concurrentPkg + ".BlockingIterable");
    static final ClassName AsyncCloseable = bestGuess(concurrentApiPkg + ".AsyncCloseable");
    static final ClassName Completable = bestGuess(concurrentApiPkg + ".Completable");
    static final ClassName Publisher = bestGuess(concurrentApiPkg + ".Publisher");
    static final ClassName Single = bestGuess(concurrentApiPkg + ".Single");

    static final ClassName BlockingGrpcService = bestGuess(grpcApiPkg + ".BlockingGrpcService");
    static final ClassName DefaultGrpcClientMetadata = bestGuess(grpcApiPkg + ".DefaultGrpcClientMetadata");
    static final ClassName GrpcExecutionStrategy = bestGuess(grpcApiPkg + ".GrpcExecutionStrategy");
    static final ClassName GrpcPayloadWriter = bestGuess(grpcApiPkg + ".GrpcPayloadWriter");
    static final ClassName GrpcRoutes = bestGuess(grpcApiPkg + ".GrpcRoutes");
    static final ClassName GrpcSerializationProvider = bestGuess(grpcApiPkg + ".GrpcSerializationProvider");
    static final ClassName GrpcService = bestGuess(grpcApiPkg + ".GrpcService");
    static final ClassName GrpcServiceContext = bestGuess(grpcApiPkg + ".GrpcServiceContext");
    static final ClassName GrpcServiceFactory = bestGuess(grpcApiPkg + ".GrpcServiceFactory");
    static final ClassName GrpcServiceFilterFactory = bestGuess(grpcApiPkg + ".GrpcServiceFilterFactory");

    static final ClassName AllGrpcRoutes = bestGuess(grpcRoutesFqcn + ".AllGrpcRoutes");
    static final ClassName RequestStreamingRoute = bestGuess(grpcRoutesFqcn + ".RequestStreamingRoute");
    static final ClassName ResponseStreamingRoute = bestGuess(grpcRoutesFqcn + ".ResponseStreamingRoute");
    static final ClassName Route = bestGuess(grpcRoutesFqcn + ".Route");
    static final ClassName StreamingRoute = bestGuess(grpcRoutesFqcn + ".StreamingRoute");

    static final ClassName ProtoBufSerializationProviderBuilder =
            bestGuess(grpcProtobufPkg + ".ProtoBufSerializationProviderBuilder");

    private Types() {
        // no instances
    }
}

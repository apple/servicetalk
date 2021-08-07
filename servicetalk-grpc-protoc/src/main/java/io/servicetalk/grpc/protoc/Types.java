/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import static com.squareup.javapoet.ClassName.bestGuess;

final class Types {
    private static final String basePkg = "io.servicetalk";
    private static final String concurrentPkg = basePkg + ".concurrent";
    private static final String concurrentApiPkg = basePkg + ".concurrent.api";
    private static final String grpcBasePkg = basePkg + ".grpc";
    private static final String encodingBasePkg = basePkg + ".encoding";
    private static final String encodingApiPkg = encodingBasePkg + ".api";
    private static final String grpcApiPkg = grpcBasePkg + ".api";
    private static final String grpcRoutesFqcn = grpcApiPkg + ".GrpcRoutes";
    private static final String grpcProtobufPkg = grpcBasePkg + ".protobuf";
    private static final String routerApiPkg = basePkg + ".router.api";
    private static final String protobufDataPkg = basePkg + ".data.protobuf";

    static final ClassName List = ClassName.get("java.util", "List");
    static final ClassName Objects = ClassName.get("java.util", "Objects");
    static final ClassName Collections = ClassName.get("java.util", "Collections");

    private static final ClassName RouteExecutionStrategyFactory =
            bestGuess(routerApiPkg + ".RouteExecutionStrategyFactory");

    static final ClassName BlockingIterable = bestGuess(concurrentPkg + ".BlockingIterable");

    static final ClassName AsyncCloseable = bestGuess(concurrentApiPkg + ".AsyncCloseable");
    static final ClassName Completable = bestGuess(concurrentApiPkg + ".Completable");
    static final ClassName Publisher = bestGuess(concurrentApiPkg + ".Publisher");
    static final ClassName Single = bestGuess(concurrentApiPkg + ".Single");

    static final ClassName BlockingGrpcClient = bestGuess(grpcApiPkg + ".BlockingGrpcClient");
    static final ClassName BlockingGrpcService = bestGuess(grpcApiPkg + ".BlockingGrpcService");
    static final ClassName GrpcClientMetadata = bestGuess(grpcApiPkg + ".GrpcClientMetadata");
    static final ClassName DefaultGrpcClientMetadata = bestGuess(grpcApiPkg + ".DefaultGrpcClientMetadata");
    static final ClassName GrpcClient = bestGuess(grpcApiPkg + ".GrpcClient");
    static final ClassName GrpcClientCallFactory = bestGuess(grpcApiPkg + ".GrpcClientCallFactory");
    static final ClassName GrpcClientFactory = bestGuess(grpcApiPkg + ".GrpcClientFactory");
    static final ClassName GrpcClientFilterFactory = bestGuess(grpcApiPkg + ".GrpcClientFilterFactory");
    static final ClassName FilterableGrpcClient = bestGuess(grpcApiPkg + ".FilterableGrpcClient");
    static final ClassName GrpcExecutionContext = bestGuess(grpcApiPkg + ".GrpcExecutionContext");
    static final ClassName GrpcExecutionStrategy = bestGuess(grpcApiPkg + ".GrpcExecutionStrategy");
    static final ClassName GrpcStatusException = bestGuess(grpcApiPkg + ".GrpcStatusException");
    static final ClassName Identity = bestGuess(encodingApiPkg + ".Identity");
    static final ClassName BufferDecoderGroup = bestGuess(encodingApiPkg + ".BufferDecoderGroup");
    static final ClassName EmptyBufferDecoderGroup = bestGuess(encodingApiPkg + ".EmptyBufferDecoderGroup");
    static final ClassName BufferEncoder = bestGuess(encodingApiPkg + ".BufferEncoder");
    static final TypeName BufferEncoderList = ParameterizedTypeName.get(List, BufferEncoder);
    static final ClassName ContentCodec = bestGuess(encodingApiPkg + ".ContentCodec");
    static final TypeName GrpcSupportedCodings = ParameterizedTypeName.get(List, ContentCodec);
    static final ClassName GrpcPayloadWriter = bestGuess(grpcApiPkg + ".GrpcPayloadWriter");
    static final ClassName GrpcRoutes = bestGuess(grpcApiPkg + ".GrpcRoutes");
    static final ClassName GrpcSerializationProvider = bestGuess(grpcApiPkg + ".GrpcSerializationProvider");
    static final ClassName GrpcBindableService = bestGuess(grpcApiPkg + ".GrpcBindableService");
    static final ClassName GrpcService = bestGuess(grpcApiPkg + ".GrpcService");
    static final ClassName GrpcServiceContext = bestGuess(grpcApiPkg + ".GrpcServiceContext");
    static final ClassName GrpcServiceFactory = bestGuess(grpcApiPkg + ".GrpcServiceFactory");
    static final ClassName GrpcServiceFilterFactory = bestGuess(grpcApiPkg + ".GrpcServiceFilterFactory");
    static final ClassName GrpcMethodDescriptor = bestGuess(grpcApiPkg + ".MethodDescriptor");
    static final ClassName GrpcMethodDescriptors = bestGuess(grpcApiPkg + ".MethodDescriptors");

    static final ClassName BlockingClientCall = bestGuess(GrpcClientCallFactory + ".BlockingClientCall");
    static final ClassName BlockingRequestStreamingClientCall =
            bestGuess(GrpcClientCallFactory + ".BlockingRequestStreamingClientCall");
    static final ClassName BlockingResponseStreamingClientCall =
            bestGuess(GrpcClientCallFactory + ".BlockingResponseStreamingClientCall");
    static final ClassName BlockingStreamingClientCall =
            bestGuess(GrpcClientCallFactory + ".BlockingStreamingClientCall");
    static final ClassName ClientCall = bestGuess(GrpcClientCallFactory + ".ClientCall");
    static final ClassName RequestStreamingClientCall =
            bestGuess(GrpcClientCallFactory + ".RequestStreamingClientCall");
    static final ClassName ResponseStreamingClientCall =
            bestGuess(GrpcClientCallFactory + ".ResponseStreamingClientCall");
    static final ClassName StreamingClientCall = bestGuess(GrpcClientCallFactory + ".StreamingClientCall");

    static final ClassName AllGrpcRoutes = bestGuess(grpcRoutesFqcn + ".AllGrpcRoutes");
    static final ClassName RequestStreamingRoute = bestGuess(grpcRoutesFqcn + ".RequestStreamingRoute");
    static final ClassName ResponseStreamingRoute = bestGuess(grpcRoutesFqcn + ".ResponseStreamingRoute");
    static final ClassName Route = bestGuess(grpcRoutesFqcn + ".Route");
    static final ClassName StreamingRoute = bestGuess(grpcRoutesFqcn + ".StreamingRoute");
    static final ClassName BlockingRequestStreamingRoute = bestGuess(grpcRoutesFqcn + ".BlockingRequestStreamingRoute");
    static final ClassName BlockingResponseStreamingRoute = bestGuess(grpcRoutesFqcn +
            ".BlockingResponseStreamingRoute");
    static final ClassName BlockingRoute = bestGuess(grpcRoutesFqcn + ".BlockingRoute");
    static final ClassName BlockingStreamingRoute = bestGuess(grpcRoutesFqcn + ".BlockingStreamingRoute");

    @Deprecated
    static final ClassName ProtoBufSerializationProviderBuilder =
            bestGuess(grpcProtobufPkg + ".ProtoBufSerializationProviderBuilder");
    static final ClassName ProtobufSerializerFactory = bestGuess(protobufDataPkg + ".ProtobufSerializerFactory");

    static final TypeName GrpcRouteExecutionStrategyFactory = ParameterizedTypeName.get(RouteExecutionStrategyFactory,
            GrpcExecutionStrategy);

    private Types() {
        // no instances
    }
}

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
import com.squareup.javapoet.WildcardTypeName;

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

    private static final TypeName Wildcard = WildcardTypeName.subtypeOf(Object.class);
    static final ClassName List = ClassName.get("java.util", "List");
    static final ClassName Iterable = ClassName.get("java.lang", "Iterable");
    static final ClassName Objects = ClassName.get("java.util", "Objects");
    static final ClassName Collections = ClassName.get("java.util", "Collections");
    static final ClassName Arrays = ClassName.get("java.util", "Arrays");
    private static final ClassName Collection = ClassName.get("java.util", "Collection");

    static final ClassName RouteExecutionStrategy =
            ClassName.get(routerApiPkg, "RouteExecutionStrategy");
    static final ClassName RouteExecutionStrategyFactory =
            ClassName.get(routerApiPkg, "RouteExecutionStrategyFactory");

    static final ClassName BlockingIterable = ClassName.get(concurrentPkg, "BlockingIterable");

    static final ClassName AsyncCloseable = ClassName.get(concurrentApiPkg, "AsyncCloseable");
    static final ClassName Completable = ClassName.get(concurrentApiPkg, "Completable");
    static final ClassName Publisher = ClassName.get(concurrentApiPkg, "Publisher");
    static final ClassName Single = ClassName.get(concurrentApiPkg, "Single");

    static final ClassName BlockingGrpcClient = ClassName.get(grpcApiPkg, "BlockingGrpcClient");
    static final ClassName BlockingGrpcService = ClassName.get(grpcApiPkg, "BlockingGrpcService");
    static final ClassName BlockingStreamingGrpcServerResponse =
            ClassName.get(grpcApiPkg, "BlockingStreamingGrpcServerResponse");
    static final ClassName GrpcClientMetadata = ClassName.get(grpcApiPkg, "GrpcClientMetadata");
    static final ClassName DefaultGrpcClientMetadata = ClassName.get(grpcApiPkg, "DefaultGrpcClientMetadata");
    static final ClassName GrpcClient = ClassName.get(grpcApiPkg, "GrpcClient");
    static final ClassName GrpcClientCallFactory = ClassName.get(grpcApiPkg, "GrpcClientCallFactory");
    static final ClassName GrpcClientFactory = ClassName.get(grpcApiPkg, "GrpcClientFactory");
    static final ClassName GrpcExecutionContext = ClassName.get(grpcApiPkg, "GrpcExecutionContext");
    static final ClassName GrpcExecutionStrategy = ClassName.get(grpcApiPkg, "GrpcExecutionStrategy");
    static final ClassName GrpcStatusException = ClassName.get(grpcApiPkg, "GrpcStatusException");
    static final ClassName Identity = ClassName.get(encodingApiPkg, "Identity");
    static final ClassName BufferDecoderGroup = ClassName.get(encodingApiPkg, "BufferDecoderGroup");
    static final ClassName EmptyBufferDecoderGroup = ClassName.get(encodingApiPkg, "EmptyBufferDecoderGroup");
    static final ClassName BufferEncoder = ClassName.get(encodingApiPkg, "BufferEncoder");
    static final TypeName BufferEncoderList = ParameterizedTypeName.get(List, BufferEncoder);
    static final ClassName ContentCodec = ClassName.get(encodingApiPkg, "ContentCodec");
    static final TypeName GrpcSupportedCodings = ParameterizedTypeName.get(List, ContentCodec);
    static final ClassName GrpcPayloadWriter = ClassName.get(grpcApiPkg, "GrpcPayloadWriter");
    static final ClassName GrpcRoutes = ClassName.get(grpcApiPkg, "GrpcRoutes");
    static final ClassName GrpcSerializationProvider = ClassName.get(grpcApiPkg, "GrpcSerializationProvider");
    static final ClassName GrpcBindableService = ClassName.get(grpcApiPkg, "GrpcBindableService");
    static final ClassName GrpcService = ClassName.get(grpcApiPkg, "GrpcService");
    static final ClassName GrpcServiceContext = ClassName.get(grpcApiPkg, "GrpcServiceContext");
    static final ClassName GrpcServiceFactory = ClassName.get(grpcApiPkg, "GrpcServiceFactory");
    static final ClassName GrpcMethodDescriptor = ClassName.get(grpcApiPkg, "MethodDescriptor");
    static final ClassName GrpcMethodDescriptors = ClassName.get(grpcApiPkg, "MethodDescriptors");
    static final ParameterizedTypeName GrpcMethodDescriptorCollection = ParameterizedTypeName.get(Collection,
            ParameterizedTypeName.get(GrpcMethodDescriptor, Wildcard, Wildcard));

    static final ClassName BlockingClientCall = GrpcClientCallFactory.nestedClass("BlockingClientCall");
    static final ClassName BlockingRequestStreamingClientCall =
            GrpcClientCallFactory.nestedClass("BlockingRequestStreamingClientCall");
    static final ClassName BlockingResponseStreamingClientCall =
            GrpcClientCallFactory.nestedClass("BlockingResponseStreamingClientCall");
    static final ClassName BlockingStreamingClientCall =
            GrpcClientCallFactory.nestedClass("BlockingStreamingClientCall");
    static final ClassName ClientCall = GrpcClientCallFactory.nestedClass("ClientCall");
    static final ClassName RequestStreamingClientCall =
            GrpcClientCallFactory.nestedClass("RequestStreamingClientCall");
    static final ClassName ResponseStreamingClientCall =
            GrpcClientCallFactory.nestedClass("ResponseStreamingClientCall");
    static final ClassName StreamingClientCall = GrpcClientCallFactory.nestedClass("StreamingClientCall");

    // Inner protected types need bestGuess to avoid adding imports which aren't visible and causing compile problems.
    static final ClassName AllGrpcRoutes = bestGuess(grpcRoutesFqcn + ".AllGrpcRoutes");
    static final ClassName RequestStreamingRoute = bestGuess(grpcRoutesFqcn + ".RequestStreamingRoute");
    static final ClassName ResponseStreamingRoute = bestGuess(grpcRoutesFqcn + ".ResponseStreamingRoute");
    static final ClassName Route = bestGuess(grpcRoutesFqcn + ".Route");
    static final ClassName StreamingRoute = bestGuess(grpcRoutesFqcn + ".StreamingRoute");
    static final ClassName BlockingRequestStreamingRoute =
            bestGuess(grpcRoutesFqcn + ".BlockingRequestStreamingRoute");
    static final ClassName BlockingResponseStreamingRoute =
            bestGuess(grpcRoutesFqcn + ".BlockingResponseStreamingRoute");
    static final ClassName BlockingRoute = bestGuess(grpcRoutesFqcn + ".BlockingRoute");
    static final ClassName BlockingStreamingRoute = bestGuess(grpcRoutesFqcn + ".BlockingStreamingRoute");

    @Deprecated
    static final ClassName ProtoBufSerializationProviderBuilder =
            ClassName.get(grpcProtobufPkg, "ProtoBufSerializationProviderBuilder");
    static final ClassName ProtobufSerializerFactory = ClassName.get(protobufDataPkg, "ProtobufSerializerFactory");

    static final TypeName GrpcRouteExecutionStrategyFactory =
            ParameterizedTypeName.get(RouteExecutionStrategyFactory, GrpcExecutionStrategy);

    private Types() {
        // no instances
    }
}

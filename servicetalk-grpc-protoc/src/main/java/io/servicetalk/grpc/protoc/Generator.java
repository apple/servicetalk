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

import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.lang.model.element.Modifier;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static com.squareup.javapoet.TypeSpec.interfaceBuilder;
import static io.servicetalk.grpc.protoc.Generator.NewRpcMethodFlag.BLOCKING;
import static io.servicetalk.grpc.protoc.Generator.NewRpcMethodFlag.CLIENT;
import static io.servicetalk.grpc.protoc.Generator.NewRpcMethodFlag.INTERFACE;
import static io.servicetalk.grpc.protoc.StringUtils.sanitizeIdentifier;
import static io.servicetalk.grpc.protoc.Types.AllGrpcRoutes;
import static io.servicetalk.grpc.protoc.Types.AsyncCloseable;
import static io.servicetalk.grpc.protoc.Types.BlockingClientCall;
import static io.servicetalk.grpc.protoc.Types.BlockingGrpcClient;
import static io.servicetalk.grpc.protoc.Types.BlockingGrpcService;
import static io.servicetalk.grpc.protoc.Types.BlockingIterable;
import static io.servicetalk.grpc.protoc.Types.BlockingRequestStreamingClientCall;
import static io.servicetalk.grpc.protoc.Types.BlockingRequestStreamingRoute;
import static io.servicetalk.grpc.protoc.Types.BlockingResponseStreamingClientCall;
import static io.servicetalk.grpc.protoc.Types.BlockingResponseStreamingRoute;
import static io.servicetalk.grpc.protoc.Types.BlockingRoute;
import static io.servicetalk.grpc.protoc.Types.BlockingStreamingClientCall;
import static io.servicetalk.grpc.protoc.Types.BlockingStreamingRoute;
import static io.servicetalk.grpc.protoc.Types.ClientCall;
import static io.servicetalk.grpc.protoc.Types.Completable;
import static io.servicetalk.grpc.protoc.Types.DefaultGrpcClientMetadata;
import static io.servicetalk.grpc.protoc.Types.FilterableGrpcClient;
import static io.servicetalk.grpc.protoc.Types.GrpcClient;
import static io.servicetalk.grpc.protoc.Types.GrpcClientCallFactory;
import static io.servicetalk.grpc.protoc.Types.GrpcClientFactory;
import static io.servicetalk.grpc.protoc.Types.GrpcClientFilterFactory;
import static io.servicetalk.grpc.protoc.Types.GrpcExecutionContext;
import static io.servicetalk.grpc.protoc.Types.GrpcExecutionStrategy;
import static io.servicetalk.grpc.protoc.Types.GrpcMessageEncoding;
import static io.servicetalk.grpc.protoc.Types.GrpcPayloadWriter;
import static io.servicetalk.grpc.protoc.Types.GrpcRouteExecutionStrategyFactory;
import static io.servicetalk.grpc.protoc.Types.GrpcRoutes;
import static io.servicetalk.grpc.protoc.Types.GrpcSerializationProvider;
import static io.servicetalk.grpc.protoc.Types.GrpcService;
import static io.servicetalk.grpc.protoc.Types.GrpcServiceContext;
import static io.servicetalk.grpc.protoc.Types.GrpcServiceFactory;
import static io.servicetalk.grpc.protoc.Types.GrpcServiceFilterFactory;
import static io.servicetalk.grpc.protoc.Types.GrpcSupportedEncodings;
import static io.servicetalk.grpc.protoc.Types.ProtoBufSerializationProviderBuilder;
import static io.servicetalk.grpc.protoc.Types.Publisher;
import static io.servicetalk.grpc.protoc.Types.RequestStreamingClientCall;
import static io.servicetalk.grpc.protoc.Types.RequestStreamingRoute;
import static io.servicetalk.grpc.protoc.Types.ResponseStreamingClientCall;
import static io.servicetalk.grpc.protoc.Types.ResponseStreamingRoute;
import static io.servicetalk.grpc.protoc.Types.Route;
import static io.servicetalk.grpc.protoc.Types.Single;
import static io.servicetalk.grpc.protoc.Types.StreamingClientCall;
import static io.servicetalk.grpc.protoc.Types.StreamingRoute;
import static io.servicetalk.grpc.protoc.Words.Blocking;
import static io.servicetalk.grpc.protoc.Words.Builder;
import static io.servicetalk.grpc.protoc.Words.Call;
import static io.servicetalk.grpc.protoc.Words.Default;
import static io.servicetalk.grpc.protoc.Words.Factory;
import static io.servicetalk.grpc.protoc.Words.Filter;
import static io.servicetalk.grpc.protoc.Words.INSTANCE;
import static io.servicetalk.grpc.protoc.Words.Metadata;
import static io.servicetalk.grpc.protoc.Words.RPC_PATH;
import static io.servicetalk.grpc.protoc.Words.Rpc;
import static io.servicetalk.grpc.protoc.Words.To;
import static io.servicetalk.grpc.protoc.Words.append;
import static io.servicetalk.grpc.protoc.Words.appendServiceFilter;
import static io.servicetalk.grpc.protoc.Words.builder;
import static io.servicetalk.grpc.protoc.Words.client;
import static io.servicetalk.grpc.protoc.Words.close;
import static io.servicetalk.grpc.protoc.Words.closeAsync;
import static io.servicetalk.grpc.protoc.Words.closeAsyncGracefully;
import static io.servicetalk.grpc.protoc.Words.closeGracefully;
import static io.servicetalk.grpc.protoc.Words.closeable;
import static io.servicetalk.grpc.protoc.Words.ctx;
import static io.servicetalk.grpc.protoc.Words.delegate;
import static io.servicetalk.grpc.protoc.Words.encoding;
import static io.servicetalk.grpc.protoc.Words.executionContext;
import static io.servicetalk.grpc.protoc.Words.existing;
import static io.servicetalk.grpc.protoc.Words.factory;
import static io.servicetalk.grpc.protoc.Words.metadata;
import static io.servicetalk.grpc.protoc.Words.onClose;
import static io.servicetalk.grpc.protoc.Words.request;
import static io.servicetalk.grpc.protoc.Words.routes;
import static io.servicetalk.grpc.protoc.Words.rpc;
import static io.servicetalk.grpc.protoc.Words.serializationProvider;
import static io.servicetalk.grpc.protoc.Words.service;
import static io.servicetalk.grpc.protoc.Words.strategy;
import static io.servicetalk.grpc.protoc.Words.strategyFactory;
import static io.servicetalk.grpc.protoc.Words.supportedEncodings;
import static java.util.EnumSet.noneOf;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.concat;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

final class Generator {
    private static final class RpcInterface {
        final MethodDescriptorProto methodProto;
        final boolean blocking;
        final ClassName className;

        private RpcInterface(final MethodDescriptorProto methodProto, final boolean blocking,
                             final ClassName className) {
            this.methodProto = methodProto;
            this.blocking = blocking;
            this.className = className;
        }
    }

    private static final class ClientMetaData {
        final MethodDescriptorProto methodProto;
        final ClassName className;

        private ClientMetaData(final MethodDescriptorProto methodProto, final ClassName className) {
            this.methodProto = methodProto;
            this.className = className;
        }
    }

    private static final class State {
        final ServiceDescriptorProto serviceProto;

        List<RpcInterface> serviceRpcInterfaces;
        ClassName serviceClass;
        ClassName blockingServiceClass;
        ClassName serviceFilterClass;
        ClassName serviceFilterFactoryClass;

        List<ClientMetaData> clientMetaDatas;
        ClassName clientClass;
        ClassName filterableClientClass;
        ClassName blockingClientClass;
        ClassName clientFilterClass;
        ClassName clientFilterFactoryClass;

        private State(final ServiceDescriptorProto serviceProto) {
            this.serviceProto = serviceProto;
        }
    }

    private final GenerationContext context;
    private final Map<String, ClassName> messageTypesMap;

    Generator(final GenerationContext context, final Map<String, ClassName> messageTypesMap) {
        this.context = context;
        this.messageTypesMap = messageTypesMap;
    }

    void generate(final ServiceDescriptorProto serviceProto) {
        final State state = new State(serviceProto);
        final TypeSpec.Builder serviceClassBuilder = context.newServiceClassBuilder(serviceProto);

        addSerializationProviderInit(state, serviceClassBuilder);

        addServiceRpcInterfaces(state, serviceClassBuilder);
        addServiceInterfaces(state, serviceClassBuilder);
        addServiceFilter(state, serviceClassBuilder);
        addServiceFilterFactory(state, serviceClassBuilder);
        addServiceFactory(state, serviceClassBuilder);

        addClientMetadata(state, serviceClassBuilder);
        addClientInterfaces(state, serviceClassBuilder);
        addClientFilter(state, serviceClassBuilder);
        addClientFilterFactory(state, serviceClassBuilder);
        addClientFactory(state, serviceClassBuilder);
    }

    private void addSerializationProviderInit(final State state, final TypeSpec.Builder serviceClassBuilder) {
        final CodeBlock.Builder staticInitBlockBuilder = CodeBlock.builder()
                .addStatement("$T builder = new $T()", ProtoBufSerializationProviderBuilder,
                        ProtoBufSerializationProviderBuilder);

        concat(state.serviceProto.getMethodList().stream()
                        .filter(MethodDescriptorProto::hasInputType)
                        .map(MethodDescriptorProto::getInputType),
                state.serviceProto.getMethodList().stream()
                        .filter(MethodDescriptorProto::hasOutputType)
                        .map(MethodDescriptorProto::getOutputType))
                .distinct()
                .map(messageTypesMap::get)
                .forEach(t -> staticInitBlockBuilder.addStatement("$L.registerMessageType($T.class, $T.parser())",
                        builder, t, t));

        staticInitBlockBuilder
                .addStatement("$L = $L.build()", serializationProvider, builder)
                .build();

        serviceClassBuilder
                .addField(GrpcSerializationProvider, serializationProvider, PRIVATE, STATIC, FINAL)
                .addStaticBlock(staticInitBlockBuilder.build());
    }

    private void addServiceRpcInterfaces(final State state, final TypeSpec.Builder serviceClassBuilder) {
        state.serviceRpcInterfaces = new ArrayList<>(2 * state.serviceProto.getMethodCount());
        state.serviceProto.getMethodList().forEach(methodProto -> {
            final String name = context.deconflictJavaTypeName(
                    sanitizeIdentifier(methodProto.getName(), false) + Rpc);

            final FieldSpec.Builder pathSpecBuilder = FieldSpec.builder(String.class, RPC_PATH, PUBLIC, STATIC, FINAL)
                    .initializer("$S", context.methodPath(state.serviceProto, methodProto));
            final TypeSpec.Builder interfaceSpecBuilder = interfaceBuilder(name)
                    .addAnnotation(FunctionalInterface.class)
                    .addModifiers(PUBLIC)
                    .addField(pathSpecBuilder.build())
                    .addMethod(newRpcMethodSpec(methodProto, EnumSet.of(INTERFACE),
                            (__, b) -> b.addModifiers(ABSTRACT).addParameter(GrpcServiceContext, ctx)))
                    .addSuperinterface(GrpcService);

            if (methodProto.hasOptions() && methodProto.getOptions().getDeprecated()) {
                interfaceSpecBuilder.addAnnotation(Deprecated.class);
            }

            final TypeSpec interfaceSpec = interfaceSpecBuilder.build();
            state.serviceRpcInterfaces.add(new RpcInterface(methodProto, false, ClassName.bestGuess(name)));
            serviceClassBuilder.addType(interfaceSpec);
        });

        List<RpcInterface> asyncRpcInterfaces = new ArrayList<>(state.serviceRpcInterfaces);
        asyncRpcInterfaces.forEach(rpcInterface -> {
            MethodDescriptorProto methodProto = rpcInterface.methodProto;
            final String name = context.deconflictJavaTypeName(Blocking + rpcInterface.className.simpleName());

            final FieldSpec.Builder pathSpecBuilder = FieldSpec.builder(String.class, RPC_PATH, PUBLIC, STATIC, FINAL);
            pathSpecBuilder.initializer("$T.$L", rpcInterface.className, RPC_PATH);
            final TypeSpec.Builder interfaceSpecBuilder = interfaceBuilder(name)
                    .addAnnotation(FunctionalInterface.class)
                    .addModifiers(PUBLIC)
                    .addField(pathSpecBuilder.build())
                    .addMethod(newRpcMethodSpec(methodProto, EnumSet.of(BLOCKING, INTERFACE),
                            (__, b) -> b.addModifiers(ABSTRACT).addParameter(GrpcServiceContext, ctx)))
                    .addSuperinterface(BlockingGrpcService);

            if (methodProto.hasOptions() && methodProto.getOptions().getDeprecated()) {
                interfaceSpecBuilder.addAnnotation(Deprecated.class);
            }

            final TypeSpec interfaceSpec = interfaceSpecBuilder.build();
            state.serviceRpcInterfaces.add(new RpcInterface(methodProto, true, ClassName.bestGuess(name)));
            serviceClassBuilder.addType(interfaceSpec);
        });
    }

    private void addServiceInterfaces(final State state, final TypeSpec.Builder serviceClassBuilder) {
        TypeSpec interfaceSpec = newServiceInterfaceSpec(state, false);
        state.serviceClass = ClassName.bestGuess(interfaceSpec.name);
        serviceClassBuilder.addType(interfaceSpec);

        interfaceSpec = newServiceInterfaceSpec(state, true);
        state.blockingServiceClass = ClassName.bestGuess(interfaceSpec.name);
        serviceClassBuilder.addType(interfaceSpec);
    }

    private void addServiceFilter(final State state, final TypeSpec.Builder serviceClassBuilder) {
        state.serviceFilterClass = state.serviceClass.peerClass(state.serviceClass.simpleName() + Filter);

        final TypeSpec.Builder classSpecBuilder =
                newFilterDelegateCommonMethods(state.serviceFilterClass, state.serviceClass);

        state.serviceProto.getMethodList().forEach(methodProto ->
                classSpecBuilder.addMethod(newRpcMethodSpec(methodProto, noneOf(NewRpcMethodFlag.class),
                        (n, b) -> b.addAnnotation(Override.class)
                                .addParameter(GrpcServiceContext, ctx, FINAL)
                                .addStatement("return $L.$L($L, $L)", delegate, n, ctx, request))));

        serviceClassBuilder.addType(classSpecBuilder.build());
    }

    private void addServiceFilterFactory(final State state, final TypeSpec.Builder serviceClassBuilder) {
        state.serviceFilterFactoryClass = state.serviceFilterClass.peerClass(state.serviceFilterClass.simpleName() +
                Factory);

        serviceClassBuilder.addType(interfaceBuilder(state.serviceFilterFactoryClass)
                .addModifiers(PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(GrpcServiceFilterFactory, state.serviceFilterClass,
                        state.serviceClass))
                .build());
    }

    private void addServiceFactory(final State state, final TypeSpec.Builder serviceClassBuilder) {
        final ClassName serviceFactoryClass = state.serviceClass.peerClass("Service" + Factory);
        final ClassName builderClass = serviceFactoryClass.nestedClass(Builder);
        final ClassName serviceFromRoutesClass = builderClass.nestedClass(state.serviceClass.simpleName() +
                "FromRoutes");

        // TODO: Warn for path override and Validate all paths are defined.
        final TypeSpec.Builder serviceBuilderSpecBuilder = classBuilder(Builder)
                .addModifiers(PUBLIC, STATIC, FINAL)
                .superclass(ParameterizedTypeName.get(GrpcRoutes, state.serviceClass))
                .addType(newServiceFromRoutesClassSpec(serviceFromRoutesClass, state.serviceRpcInterfaces,
                        state.serviceClass))
                .addMethod(constructorBuilder()
                        .addModifiers(PUBLIC)
                        .build())
                .addMethod(constructorBuilder()
                        .addModifiers(PUBLIC)
                        .addParameter(GrpcSupportedEncodings, supportedEncodings, FINAL)
                        .addStatement("super($L)", supportedEncodings)
                        .build())
                .addMethod(constructorBuilder()
                        .addModifiers(PUBLIC)
                        .addParameter(GrpcRouteExecutionStrategyFactory, strategyFactory, FINAL)
                        .addStatement("super($L)", strategyFactory)
                        .build())
                .addMethod(constructorBuilder()
                        .addModifiers(PUBLIC)
                        .addParameter(GrpcRouteExecutionStrategyFactory, strategyFactory, FINAL)
                        .addParameter(GrpcSupportedEncodings, supportedEncodings, FINAL)
                        .addStatement("super($L, $L)", strategyFactory, supportedEncodings)
                        .build())
                .addMethod(methodBuilder("build")
                        .addModifiers(PUBLIC)
                        .returns(serviceFactoryClass)
                        .addStatement("return new $T(this)", serviceFactoryClass)
                        .build())
                .addMethod(methodBuilder("newServiceFromRoutes")
                        .addModifiers(PROTECTED)
                        .addAnnotation(Override.class)
                        .returns(serviceFromRoutesClass)
                        .addParameter(AllGrpcRoutes, routes, FINAL)
                        .addStatement("return new $T($L)", serviceFromRoutesClass, routes)
                        .build());

        state.serviceRpcInterfaces.forEach(rpcInterface -> {
            final ClassName inClass = messageTypesMap.get(rpcInterface.methodProto.getInputType());
            final ClassName outClass = messageTypesMap.get(rpcInterface.methodProto.getOutputType());
            final String routeName = routeName(rpcInterface.methodProto);
            final String methodName = routeName + (rpcInterface.blocking ? Blocking : "");
            final String addRouteMethodName = addRouteMethodName(rpcInterface.methodProto, rpcInterface.blocking);
            final ClassName routeInterfaceClass = routeInterfaceClass(rpcInterface.methodProto, rpcInterface.blocking);

            serviceBuilderSpecBuilder
                    .addMethod(methodBuilder(methodName)
                            .addModifiers(PUBLIC)
                            .addParameter(rpcInterface.className, rpc, FINAL)
                            .returns(builderClass)
                            .addStatement("$L($T.$L, $L.getClass(), $S, $L.wrap($L::$L, $L), $T.class, $T.class, $L)",
                                    addRouteMethodName, rpcInterface.className, RPC_PATH, rpc, routeName,
                                    routeInterfaceClass, rpc, routeName, rpc, inClass, outClass, serializationProvider)
                            .addStatement("return this")
                            .build())
                    .addMethod(methodBuilder(methodName)
                            .addModifiers(PUBLIC)
                            .addParameter(GrpcExecutionStrategy, strategy, FINAL)
                            .addParameter(rpcInterface.className, rpc, FINAL)
                            .returns(builderClass)
                            .addStatement("$L($T.$L, $L, $L.wrap($L::$L, $L), $T.class, $T.class, $L)",
                                    addRouteMethodName, rpcInterface.className, RPC_PATH, strategy,
                                    routeInterfaceClass, rpc, routeName, rpc, inClass, outClass, serializationProvider)
                            .addStatement("return this")
                            .build());
        });

        final MethodSpec.Builder registerRoutesMethodSpecBuilder = methodBuilder("registerRoutes")
                .addModifiers(PROTECTED)
                .addAnnotation(Override.class)
                .addParameter(state.serviceClass, service, FINAL);

        state.serviceProto.getMethodList().stream()
                .map(Generator::routeName)
                .forEach(n -> registerRoutesMethodSpecBuilder.addStatement("$L($L)", n, service));

        final TypeSpec serviceBuilderType = serviceBuilderSpecBuilder
                .addMethod(registerRoutesMethodSpecBuilder.build())
                .build();

        final TypeSpec.Builder serviceFactoryClassSpecBuilder = classBuilder(serviceFactoryClass)
                .addModifiers(PUBLIC, STATIC, FINAL)
                .superclass(ParameterizedTypeName.get(GrpcServiceFactory, state.serviceFilterClass, state.serviceClass,
                        state.serviceFilterFactoryClass))
                .addMethod(constructorBuilder()
                        .addModifiers(PUBLIC)
                        .addParameter(state.serviceClass, service, FINAL)
                        .addStatement("super(new $T().$L)", builderClass,
                                serviceFactoryBuilderInitChain(state.serviceProto, false))
                        .build())
                .addMethod(constructorBuilder()
                        .addModifiers(PUBLIC)
                        .addParameter(state.serviceClass, service, FINAL)
                        .addParameter(GrpcSupportedEncodings, supportedEncodings, FINAL)
                        .addStatement("super(new $T($L).$L)", builderClass, supportedEncodings,
                                serviceFactoryBuilderInitChain(state.serviceProto, false))
                        .build())
                .addMethod(constructorBuilder()
                        .addModifiers(PUBLIC)
                        .addParameter(state.serviceClass, service, FINAL)
                        .addParameter(GrpcRouteExecutionStrategyFactory, strategyFactory, FINAL)
                        .addStatement("super(new $T($L).$L)", builderClass, strategyFactory,
                                serviceFactoryBuilderInitChain(state.serviceProto, false))
                        .build())
                .addMethod(constructorBuilder()
                        .addModifiers(PUBLIC)
                        .addParameter(state.serviceClass, service, FINAL)
                        .addParameter(GrpcRouteExecutionStrategyFactory, strategyFactory, FINAL)
                        .addParameter(GrpcSupportedEncodings, supportedEncodings, FINAL)
                        .addStatement("super(new $T($L, $L).$L)", builderClass, strategyFactory, supportedEncodings,
                                serviceFactoryBuilderInitChain(state.serviceProto, false))
                        .build())
                .addMethod(constructorBuilder()
                        .addModifiers(PUBLIC)
                        .addParameter(state.blockingServiceClass, service, FINAL)
                        .addStatement("super(new $T().$L)", builderClass,
                                serviceFactoryBuilderInitChain(state.serviceProto, true))
                        .build())
                .addMethod(constructorBuilder()
                        .addModifiers(PUBLIC)
                        .addParameter(state.blockingServiceClass, service, FINAL)
                        .addParameter(GrpcRouteExecutionStrategyFactory, strategyFactory, FINAL)
                        .addStatement("super(new $T($L).$L)", builderClass, strategyFactory,
                                serviceFactoryBuilderInitChain(state.serviceProto, true))
                        .build())
                .addMethod(constructorBuilder()
                        .addModifiers(PRIVATE)
                        .addParameter(builderClass, builder, FINAL)
                        .addStatement("super($L)", builder)
                        .build())
                .addMethod(methodBuilder(appendServiceFilter)
                        .addModifiers(PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(serviceFactoryClass)
                        .addParameter(state.serviceFilterFactoryClass, factory, FINAL)
                        .addStatement("super.$L($L)", appendServiceFilter, factory)
                        .addStatement("return this")
                        .build())
                .addMethod(methodBuilder("appendServiceFilterFactory")
                        .addModifiers(PROTECTED)
                        .addAnnotation(Override.class)
                        .returns(state.serviceFilterFactoryClass)
                        .addParameter(state.serviceFilterFactoryClass, existing, FINAL)
                        .addParameter(state.serviceFilterFactoryClass, append, FINAL)
                        .addStatement("return $L -> $L.create($L.create($L))", service, existing, append, service)
                        .build())
                .addType(serviceBuilderType);

        serviceClassBuilder.addType(serviceFactoryClassSpecBuilder.build());
    }

    private void addClientMetadata(final State state, final TypeSpec.Builder serviceClassBuilder) {
        state.clientMetaDatas = new ArrayList<>(state.serviceProto.getMethodCount());

        state.serviceRpcInterfaces.stream().filter(rpcInterface -> !rpcInterface.blocking).forEach(rpcInterface -> {
            MethodDescriptorProto methodProto = rpcInterface.methodProto;
            final String name = context.deconflictJavaTypeName(sanitizeIdentifier(methodProto.getName(), false) +
                    Metadata);

            final ClassName metaDataClassName = ClassName.bestGuess(name);
            final TypeSpec classSpec = classBuilder(name)
                    .addModifiers(PUBLIC, STATIC, FINAL)
                    .superclass(DefaultGrpcClientMetadata)
                    .addField(FieldSpec.builder(metaDataClassName, INSTANCE, PUBLIC, STATIC, FINAL)
                            .initializer("new $T()", metaDataClassName)
                            .build())
                    .addMethod(constructorBuilder()
                            .addModifiers(PRIVATE)
                            .addStatement("super($T.$L)", rpcInterface.className, RPC_PATH)
                            .build())
                    .addMethod(constructorBuilder()
                            .addModifiers(PUBLIC)
                            .addParameter(GrpcMessageEncoding, encoding, FINAL)
                            .addStatement("super($T.$L, $L)", rpcInterface.className, RPC_PATH, encoding)
                            .build())
                    .addMethod(constructorBuilder()
                            .addModifiers(PUBLIC)
                            .addParameter(GrpcExecutionStrategy, strategy, FINAL)
                            .addStatement("super($T.$L, $L)", rpcInterface.className, RPC_PATH, strategy)
                            .build())
                    .addMethod(constructorBuilder()
                            .addModifiers(PUBLIC)
                            .addParameter(GrpcExecutionStrategy, strategy, FINAL)
                            .addParameter(GrpcMessageEncoding, encoding, FINAL)
                            .addStatement("super($T.$L, $L, $L)", rpcInterface.className, RPC_PATH, strategy, encoding)
                            .build())
                    .build();

            state.clientMetaDatas.add(new ClientMetaData(methodProto, metaDataClassName));
            serviceClassBuilder.addType(classSpec);
        });
    }

    private void addClientInterfaces(final State state, final TypeSpec.Builder serviceClassBuilder) {
        state.clientClass = ClassName.bestGuess(sanitizeIdentifier(state.serviceProto.getName(), false) + "Client");
        state.filterableClientClass = state.clientClass.peerClass("Filterable" + state.clientClass.simpleName());
        state.blockingClientClass = state.clientClass.peerClass(Blocking + state.clientClass.simpleName());

        final TypeSpec.Builder clientSpecBuilder = interfaceBuilder(state.clientClass)
                .addModifiers(PUBLIC)
                .addSuperinterface(state.filterableClientClass)
                .addSuperinterface(ParameterizedTypeName.get(GrpcClient, state.blockingClientClass));

        final TypeSpec.Builder filterableClientSpecBuilder = interfaceBuilder(state.filterableClientClass)
                .addModifiers(PUBLIC)
                .addSuperinterface(FilterableGrpcClient);

        final TypeSpec.Builder blockingClientSpecBuilder = interfaceBuilder(state.blockingClientClass)
                .addModifiers(PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(BlockingGrpcClient, state.clientClass));

        state.clientMetaDatas.forEach(clientMetaData -> {
            clientSpecBuilder
                    .addMethod(newRpcMethodSpec(clientMetaData.methodProto, EnumSet.of(INTERFACE, CLIENT),
                            (__, b) -> b.addModifiers(ABSTRACT)));

            filterableClientSpecBuilder
                    .addMethod(newRpcMethodSpec(clientMetaData.methodProto, EnumSet.of(INTERFACE, CLIENT),
                            (__, b) -> b.addModifiers(ABSTRACT)
                                    .addParameter(clientMetaData.className, metadata)));

            blockingClientSpecBuilder
                    .addMethod(newRpcMethodSpec(clientMetaData.methodProto, EnumSet.of(BLOCKING, INTERFACE, CLIENT),
                            (__, b) -> b.addModifiers(ABSTRACT)))
                    .addMethod(newRpcMethodSpec(clientMetaData.methodProto, EnumSet.of(BLOCKING, INTERFACE, CLIENT),
                            (__, b) -> b.addModifiers(ABSTRACT)
                                    .addParameter(clientMetaData.className, metadata)));
        });

        serviceClassBuilder.addType(clientSpecBuilder.build())
                .addType(filterableClientSpecBuilder.build())
                .addType(blockingClientSpecBuilder.build());
    }

    private void addClientFilter(final State state, final TypeSpec.Builder serviceClassBuilder) {
        state.clientFilterClass = state.clientClass.peerClass(state.clientClass.simpleName() + Filter);

        final TypeSpec.Builder classSpecBuilder = newFilterDelegateCommonMethods(state.clientFilterClass,
                state.filterableClientClass)
                .addMethod(newDelegatingCompletableMethodSpec(onClose, delegate))
                .addMethod(newDelegatingMethodSpec(executionContext, delegate, GrpcExecutionContext, null));

        state.clientMetaDatas.forEach(clientMetaData ->
                classSpecBuilder.addMethod(newRpcMethodSpec(clientMetaData.methodProto, EnumSet.of(INTERFACE, CLIENT),
                        (n, b) -> b.addAnnotation(Override.class)
                                .addParameter(clientMetaData.className, metadata)
                                .addStatement("return $L.$L($L, $L)", delegate, n, metadata, request))));

        serviceClassBuilder.addType(classSpecBuilder.build());
    }

    private void addClientFilterFactory(final State state, final TypeSpec.Builder serviceClassBuilder) {
        state.clientFilterFactoryClass = state.clientFilterClass.peerClass(state.clientFilterClass.simpleName() +
                Factory);

        serviceClassBuilder.addType(interfaceBuilder(state.clientFilterFactoryClass)
                .addModifiers(PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(GrpcClientFilterFactory, state.clientFilterClass,
                        state.filterableClientClass))
                .build());
    }

    private void addClientFactory(final State state, final TypeSpec.Builder serviceClassBuilder) {
        final ClassName clientFactoryClass = state.clientClass.peerClass("Client" + Factory);
        final ClassName defaultClientClass = clientFactoryClass.peerClass(Default + state.clientClass.simpleName());
        final ClassName filterableClientToClientClass = clientFactoryClass.peerClass(
                state.filterableClientClass.simpleName() + To + state.clientClass.simpleName());
        final ClassName defaultBlockingClientClass = clientFactoryClass.peerClass(Default +
                state.blockingClientClass.simpleName());
        final ClassName clientToBlockingClientClass = clientFactoryClass.peerClass(state.clientClass.simpleName() + To +
                state.blockingClientClass.simpleName());

        final TypeSpec.Builder clientFactorySpecBuilder = classBuilder(clientFactoryClass)
                .addModifiers(PUBLIC, STATIC, FINAL)
                .superclass(ParameterizedTypeName.get(GrpcClientFactory, state.clientClass, state.blockingClientClass,
                        state.clientFilterClass, state.filterableClientClass, state.clientFilterFactoryClass))
                .addMethod(methodBuilder("appendClientFilterFactory")
                        .addModifiers(PROTECTED)
                        .addAnnotation(Override.class)
                        .returns(state.clientFilterFactoryClass)
                        .addParameter(state.clientFilterFactoryClass, existing, FINAL)
                        .addParameter(state.clientFilterFactoryClass, append, FINAL)
                        .addStatement("return $L -> $L.create($L.create($L))", client, existing, append, client)
                        .build())
                .addMethod(methodBuilder("newClient")
                        .addModifiers(PROTECTED)
                        .addAnnotation(Override.class)
                        .returns(state.clientClass)
                        .addParameter(GrpcClientCallFactory, factory, FINAL)
                        .addParameter(GrpcSupportedEncodings, supportedEncodings, FINAL)
                        .addStatement("return new $T($L, $L)", defaultClientClass, factory, supportedEncodings)
                        .build())
                .addMethod(methodBuilder("newFilter")
                        .addModifiers(PROTECTED)
                        .addAnnotation(Override.class)
                        .returns(state.clientFilterClass)
                        .addParameter(state.clientClass, client, FINAL)
                        .addParameter(state.clientFilterFactoryClass, factory, FINAL)
                        .addStatement("return $L.create($L)", factory, client)
                        .build())
                .addMethod(methodBuilder("newClient")
                        .addModifiers(PROTECTED)
                        .addAnnotation(Override.class)
                        .returns(state.clientClass)
                        .addParameter(state.filterableClientClass, client, FINAL)
                        .addStatement("return new $T($L)", filterableClientToClientClass, client)
                        .build())
                .addMethod(methodBuilder("newBlockingClient")
                        .addModifiers(PROTECTED)
                        .addAnnotation(Override.class)
                        .returns(state.blockingClientClass)
                        .addParameter(GrpcClientCallFactory, factory, FINAL)
                        .addParameter(GrpcSupportedEncodings, supportedEncodings, FINAL)
                        .addStatement("return new $T($L, $L)", defaultBlockingClientClass, factory, supportedEncodings)
                        .build())
                .addType(newDefaultClientClassSpec(state, defaultClientClass, defaultBlockingClientClass))
                .addType(newFilterableClientToClientClassSpec(state, filterableClientToClientClass,
                        clientToBlockingClientClass))
                .addType(newDefaultBlockingClientClassSpec(state, defaultClientClass, defaultBlockingClientClass))
                .addType(newClientToBlockingClientClassSpec(state, clientToBlockingClientClass));

        serviceClassBuilder.addType(clientFactorySpecBuilder.build());
    }

    private TypeSpec newServiceFromRoutesClassSpec(final ClassName serviceFromRoutesClass,
                                                   final List<RpcInterface> rpcInterfaces,
                                                   final ClassName serviceClass) {
        final TypeSpec.Builder serviceFromRoutesSpecBuilder = classBuilder(serviceFromRoutesClass)
                .addModifiers(PRIVATE, STATIC, FINAL)
                .addSuperinterface(serviceClass)
                .addField(AsyncCloseable, closeable, PRIVATE, FINAL);

        final MethodSpec.Builder serviceFromRoutesConstructorBuilder = constructorBuilder()
                .addModifiers(PRIVATE)
                .addParameter(AllGrpcRoutes, routes, FINAL)
                .addStatement("$L = $L", closeable, routes);

        rpcInterfaces.stream().filter(rpcInterface -> !rpcInterface.blocking).forEach(rpc -> {
            MethodDescriptorProto methodProto = rpc.methodProto;
            final ClassName inClass = messageTypesMap.get(methodProto.getInputType());
            final ClassName outClass = messageTypesMap.get(methodProto.getOutputType());
            final String routeName = routeName(methodProto);

            serviceFromRoutesSpecBuilder.addField(ParameterizedTypeName.get(routeInterfaceClass(methodProto),
                    inClass, outClass), routeName, PRIVATE, FINAL);

            serviceFromRoutesConstructorBuilder.addStatement("$L = $L.$L($T.$L)", routeName, routes,
                    routeFactoryMethodName(methodProto), rpc.className, RPC_PATH);

            serviceFromRoutesSpecBuilder.addMethod(newRpcMethodSpec(methodProto, noneOf(NewRpcMethodFlag.class),
                    (name, builder) ->
                            builder.addAnnotation(Override.class)
                                    .addParameter(GrpcServiceContext, ctx, FINAL)
                                    .addStatement("return $L.handle($L, $L)", routeName, ctx, request)));
        });

        serviceFromRoutesSpecBuilder
                .addMethod(serviceFromRoutesConstructorBuilder.build())
                .addMethod(newDelegatingCompletableMethodSpec(closeAsync, closeable))
                .addMethod(newDelegatingCompletableMethodSpec(closeAsyncGracefully, closeable));

        return serviceFromRoutesSpecBuilder.build();
    }

    enum NewRpcMethodFlag {
        BLOCKING, INTERFACE, CLIENT
    }

    private MethodSpec newRpcMethodSpec(final MethodDescriptorProto methodProto, final EnumSet<NewRpcMethodFlag> flags,
                                        final BiFunction<String, MethodSpec.Builder, MethodSpec.Builder>
                                                methodBuilderCustomizer) {

        final ClassName inClass = messageTypesMap.get(methodProto.getInputType());
        final ClassName outClass = messageTypesMap.get(methodProto.getOutputType());

        final String name = routeName(methodProto);

        final MethodSpec.Builder methodSpecBuilder = methodBuilderCustomizer.apply(name, methodBuilder(name))
                .addModifiers(PUBLIC);

        final Modifier[] mods = flags.contains(INTERFACE) ? new Modifier[0] : new Modifier[]{FINAL};

        if (flags.contains(BLOCKING)) {
            methodSpecBuilder.addException(Exception.class);

            if (methodProto.getClientStreaming()) {
                if (flags.contains(CLIENT)) {
                    methodSpecBuilder.addParameter(ParameterizedTypeName.get(ClassName.get(Iterable.class),
                            inClass), request, mods);
                } else {
                    methodSpecBuilder.addParameter(ParameterizedTypeName.get(BlockingIterable, inClass), request,
                            mods);
                }
            } else {
                methodSpecBuilder.addParameter(inClass, request, mods);
            }

            if (methodProto.getServerStreaming()) {
                if (flags.contains(CLIENT)) {
                    methodSpecBuilder.returns(ParameterizedTypeName.get(BlockingIterable, outClass));
                } else {
                    methodSpecBuilder.addParameter(ParameterizedTypeName.get(GrpcPayloadWriter, outClass),
                            "responseWriter", mods);
                }
            } else {
                methodSpecBuilder.returns(outClass);
            }
        } else {
            if (methodProto.getClientStreaming()) {
                methodSpecBuilder.addParameter(ParameterizedTypeName.get(Publisher, inClass), request, mods);
            } else {
                methodSpecBuilder.addParameter(inClass, request, mods);
            }

            if (methodProto.getServerStreaming()) {
                methodSpecBuilder.returns(ParameterizedTypeName.get(Publisher, outClass));
            } else {
                methodSpecBuilder.returns(ParameterizedTypeName.get(Single, outClass));
            }
        }

        return methodSpecBuilder.build();
    }

    private TypeSpec newDefaultBlockingClientClassSpec(final State state, final ClassName defaultClientClass,
                                                       final ClassName defaultBlockingClientClass) {
        final TypeSpec.Builder typeSpecBuilder = classBuilder(defaultBlockingClientClass)
                .addModifiers(PRIVATE, STATIC, FINAL)
                .addSuperinterface(state.blockingClientClass)
                .addField(GrpcClientCallFactory, factory, PRIVATE, FINAL)
                .addField(GrpcSupportedEncodings, supportedEncodings, PRIVATE, FINAL)
                .addMethod(methodBuilder("asClient")
                        .addModifiers(PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(state.clientClass)
                        // TODO: Cache client
                        .addStatement("return new $T($L, $L)", defaultClientClass, factory, supportedEncodings)
                        .build())
                .addMethod(newDelegatingMethodSpec(executionContext, factory, GrpcExecutionContext, null))
                .addMethod(newDelegatingCompletableToBlockingMethodSpec(close, closeAsync, factory))
                .addMethod(newDelegatingCompletableToBlockingMethodSpec(closeGracefully, closeAsyncGracefully,
                        factory));

        final MethodSpec.Builder constructorBuilder = constructorBuilder()
                .addModifiers(PRIVATE)
                .addParameter(GrpcClientCallFactory, factory, FINAL)
                .addParameter(GrpcSupportedEncodings, supportedEncodings, FINAL)
                .addStatement("this.$N = $N", factory, factory)
                .addStatement("this.$N = $N", supportedEncodings, supportedEncodings);

        addClientFieldsAndMethods(state, typeSpecBuilder, constructorBuilder, true);

        typeSpecBuilder.addMethod(constructorBuilder.build());
        return typeSpecBuilder.build();
    }

    private TypeSpec newDefaultClientClassSpec(final State state, final ClassName defaultClientClass,
                                               final ClassName defaultBlockingClientClass) {
        final TypeSpec.Builder typeSpecBuilder = classBuilder(defaultClientClass)
                .addModifiers(PRIVATE, STATIC, FINAL)
                .addSuperinterface(state.clientClass)
                .addField(GrpcClientCallFactory, factory, PRIVATE, FINAL)
                .addField(GrpcSupportedEncodings, supportedEncodings, PRIVATE, FINAL)
                .addMethod(methodBuilder("asBlockingClient")
                        .addModifiers(PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(state.blockingClientClass)
                        // TODO: Cache client
                        .addStatement("return new $T($L, $L)", defaultBlockingClientClass, factory, supportedEncodings)
                        .build())
                .addMethod(newDelegatingMethodSpec(executionContext, factory, GrpcExecutionContext, null))
                .addMethod(newDelegatingCompletableMethodSpec(onClose, factory))
                .addMethod(newDelegatingCompletableMethodSpec(closeAsync, factory))
                .addMethod(newDelegatingCompletableMethodSpec(closeAsyncGracefully, factory))
                .addMethod(newDelegatingCompletableToBlockingMethodSpec(close, closeAsync, factory))
                .addMethod(newDelegatingCompletableToBlockingMethodSpec(closeGracefully, closeAsyncGracefully,
                        factory));

        final MethodSpec.Builder constructorBuilder = constructorBuilder()
                .addModifiers(PRIVATE)
                .addParameter(GrpcClientCallFactory, factory, FINAL)
                .addParameter(GrpcSupportedEncodings, supportedEncodings, FINAL)
                .addStatement("this.$N = $N", factory, factory)
                .addStatement("this.$N = $N", supportedEncodings, supportedEncodings);

        addClientFieldsAndMethods(state, typeSpecBuilder, constructorBuilder, false);

        typeSpecBuilder.addMethod(constructorBuilder.build());
        return typeSpecBuilder.build();
    }

    private void addClientFieldsAndMethods(final State state, final TypeSpec.Builder typeSpecBuilder,
                                           final MethodSpec.Builder constructorBuilder,
                                           final boolean blocking) {

        final EnumSet<NewRpcMethodFlag> rpcMethodSpecsFlags =
                blocking ? EnumSet.of(BLOCKING, CLIENT) : EnumSet.of(CLIENT);

        state.clientMetaDatas.forEach(clientMetaData -> {
            final ClassName inClass = messageTypesMap.get(clientMetaData.methodProto.getInputType());
            final ClassName outClass = messageTypesMap.get(clientMetaData.methodProto.getOutputType());
            final String routeName = routeName(clientMetaData.methodProto);
            final String callFieldName = routeName + Call;

            typeSpecBuilder
                    .addField(ParameterizedTypeName.get(clientCallClass(clientMetaData.methodProto, blocking),
                            inClass, outClass), callFieldName, PRIVATE, FINAL)
                    .addMethod(newRpcMethodSpec(clientMetaData.methodProto, rpcMethodSpecsFlags,
                            (n, b) -> b.addAnnotation(Override.class)
                                    .addStatement("return $L($T.$L, $L)", n, clientMetaData.className, INSTANCE,
                                            request)))
                    .addMethod(newRpcMethodSpec(clientMetaData.methodProto, rpcMethodSpecsFlags,
                            (__, b) -> b.addAnnotation(Override.class)
                                    .addParameter(clientMetaData.className, metadata, FINAL)
                                    .addStatement("return $L.$L($L, $L)", callFieldName, request, metadata, request)));

            constructorBuilder
                    .addStatement("$L = $N.$L($L, $L, $T.class, $T.class)", callFieldName, factory,
                            newCallMethodName(clientMetaData.methodProto, blocking), serializationProvider,
                                                supportedEncodings, inClass, outClass);
        });
    }

    private TypeSpec newFilterableClientToClientClassSpec(final State state,
                                                          final ClassName filterableClientToClientClass,
                                                          final ClassName clientToBlockingClientClass) {
        final TypeSpec.Builder typeSpecBuilder = classBuilder(filterableClientToClientClass)
                .addModifiers(PRIVATE, STATIC, FINAL)
                .addSuperinterface(state.clientClass)
                .addField(state.filterableClientClass, client, PRIVATE, FINAL)
                .addMethod(constructorBuilder()
                        .addModifiers(PRIVATE)
                        .addParameter(state.filterableClientClass, client, FINAL)
                        .addStatement("this.$L = $L", client, client)
                        .build())
                .addMethod(methodBuilder("asBlockingClient")
                        .addModifiers(PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(state.blockingClientClass)
                        // TODO: Cache client
                        .addStatement("return new $T(this)", clientToBlockingClientClass)
                        .build())
                .addMethod(newDelegatingMethodSpec(executionContext, client, GrpcExecutionContext, null))
                .addMethod(newDelegatingCompletableMethodSpec(onClose, client))
                .addMethod(newDelegatingCompletableMethodSpec(closeAsync, client))
                .addMethod(newDelegatingCompletableMethodSpec(closeAsyncGracefully, client));

        state.clientMetaDatas.forEach(clientMetaData -> typeSpecBuilder
                .addMethod(newRpcMethodSpec(clientMetaData.methodProto, EnumSet.of(CLIENT),
                        (n, b) -> b.addAnnotation(Override.class)
                                .addStatement("return $L($T.$L, $L)", n, clientMetaData.className, INSTANCE, request)))
                .addMethod(newRpcMethodSpec(clientMetaData.methodProto, EnumSet.of(CLIENT),
                        (n, b) -> b.addAnnotation(Override.class)
                                .addParameter(clientMetaData.className, metadata, FINAL)
                                .addStatement("return $L.$L($L, $L)", client, n, metadata, request))));

        return typeSpecBuilder.build();
    }

    private TypeSpec newClientToBlockingClientClassSpec(final State state,
                                                        final ClassName clientToBlockingClientClass) {
        final TypeSpec.Builder typeSpecBuilder = classBuilder(clientToBlockingClientClass)
                .addModifiers(PRIVATE, STATIC, FINAL)
                .addSuperinterface(state.blockingClientClass)
                .addField(state.clientClass, client, PRIVATE, FINAL)
                .addMethod(constructorBuilder()
                        .addModifiers(PRIVATE)
                        .addParameter(state.clientClass, client, FINAL)
                        .addStatement("this.$L = $L", client, client)
                        .build())
                .addMethod(methodBuilder("asClient")
                        .addModifiers(PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(state.clientClass)
                        // TODO: Cache client
                        .addStatement("return $L", client)
                        .build())
                .addMethod(newDelegatingMethodSpec(executionContext, client, GrpcExecutionContext, null))
                .addMethod(newDelegatingMethodSpec(close, client, null, ClassName.get(Exception.class)));

        state.clientMetaDatas.forEach(clientMetaData -> {
            final CodeBlock requestExpression = clientMetaData.methodProto.getClientStreaming() ?
                    CodeBlock.of("$T.fromIterable($L)", Publisher, request) : CodeBlock.of(request);
            final String responseConversionExpression = clientMetaData.methodProto.getServerStreaming() ?
                    ".toIterable()" : ".toFuture().get()";

            typeSpecBuilder
                    .addMethod(newRpcMethodSpec(clientMetaData.methodProto, EnumSet.of(BLOCKING, CLIENT),
                            (n, b) -> b.addAnnotation(Override.class)
                                    .addStatement("return $L.$L($L)$L", client, n, requestExpression,
                                            responseConversionExpression)))
                    .addMethod(newRpcMethodSpec(clientMetaData.methodProto, EnumSet.of(BLOCKING, CLIENT),
                            (n, b) -> b.addAnnotation(Override.class)
                                    .addParameter(clientMetaData.className, metadata, FINAL)
                                    .addStatement("return $L.$L($L, $L)$L", client, n, metadata, requestExpression,
                                            responseConversionExpression)));
        });

        return typeSpecBuilder.build();
    }

    private TypeSpec newServiceInterfaceSpec(final State state, final boolean blocking) {
        final String name = context.deconflictJavaTypeName((blocking ? Blocking : "") +
                sanitizeIdentifier(state.serviceProto.getName(), false) + "Service");

        final TypeSpec.Builder interfaceSpecBuilder = interfaceBuilder(name)
                .addModifiers(PUBLIC)
                .addSuperinterface(blocking ? BlockingGrpcService : GrpcService);

        state.serviceRpcInterfaces.stream()
                .filter(e -> e.blocking == blocking)
                .map(e -> e.className)
                .forEach(interfaceSpecBuilder::addSuperinterface);

        return interfaceSpecBuilder.build();
    }

    private static TypeSpec.Builder newFilterDelegateCommonMethods(final ClassName filterClass,
                                                                   final ClassName filteredClass) {
        return classBuilder(filterClass)
                .addModifiers(PUBLIC, STATIC)
                .addSuperinterface(filteredClass)
                .addField(filteredClass, delegate, PRIVATE, FINAL)
                .addMethod(constructorBuilder()
                        .addModifiers(PROTECTED)
                        .addParameter(filteredClass, delegate, FINAL)
                        .addStatement("this.$L = $L", delegate, delegate)
                        .build())
                .addMethod(methodBuilder(delegate)
                        .addModifiers(PROTECTED)
                        .returns(filteredClass)
                        .addStatement("return $L", delegate)
                        .build())
                .addMethod(newDelegatingCompletableMethodSpec(closeAsync, delegate))
                .addMethod(newDelegatingCompletableMethodSpec(closeAsyncGracefully, delegate));
    }

    private static String routeName(final MethodDescriptorProto methodProto) {
        return sanitizeIdentifier(methodProto.getName(), true);
    }

    private static ClassName routeInterfaceClass(final MethodDescriptorProto methodProto) {
        return methodProto.getClientStreaming() ?
                (methodProto.getServerStreaming() ? StreamingRoute : RequestStreamingRoute) :
                (methodProto.getServerStreaming() ? ResponseStreamingRoute : Route);
    }

    private static ClassName routeInterfaceClass(final MethodDescriptorProto methodProto, final boolean blocking) {
        return methodProto.getClientStreaming() ?
                (methodProto.getServerStreaming() ? blocking ? BlockingStreamingRoute : StreamingRoute :
                        blocking ? BlockingRequestStreamingRoute : RequestStreamingRoute) :
                (methodProto.getServerStreaming() ? blocking ? BlockingResponseStreamingRoute : ResponseStreamingRoute
                        : blocking ? BlockingRoute : Route);
    }

    private static String routeFactoryMethodName(final MethodDescriptorProto methodProto) {
        return (methodProto.getClientStreaming() ?
                (methodProto.getServerStreaming() ? "streamingR" : "requestStreamingR") :
                (methodProto.getServerStreaming() ? "responseStreamingR" : "r")) +
                "outeFor";
    }

    private static String addRouteMethodName(final MethodDescriptorProto methodProto, final boolean blocking) {
        return "add" + (blocking ? Blocking : "") + streamingNameModifier(methodProto) + "Route";
    }

    private static String serviceFactoryBuilderInitChain(final ServiceDescriptorProto serviceProto,
                                                         final boolean blocking) {
        return serviceProto.getMethodList().stream()
                .map(methodProto -> routeName(methodProto) + (blocking ? Blocking : "") + '(' + service + ')')
                .collect(joining("."));
    }

    private static ClassName clientCallClass(final MethodDescriptorProto methodProto, final boolean blocking) {
        if (!blocking) {
            return methodProto.getClientStreaming() ?
                    (methodProto.getServerStreaming() ? StreamingClientCall : RequestStreamingClientCall) :
                    (methodProto.getServerStreaming() ? ResponseStreamingClientCall : ClientCall);
        }

        return methodProto.getClientStreaming() ?
                (methodProto.getServerStreaming() ? BlockingStreamingClientCall : BlockingRequestStreamingClientCall) :
                (methodProto.getServerStreaming() ? BlockingResponseStreamingClientCall : BlockingClientCall);
    }

    private static String newCallMethodName(final MethodDescriptorProto methodProto, final boolean blocking) {
        return "new" + (blocking ? Blocking : "") + streamingNameModifier(methodProto) + Call;
    }

    private static String streamingNameModifier(final MethodDescriptorProto methodProto) {
        return methodProto.getClientStreaming() ?
                (methodProto.getServerStreaming() ? "Streaming" : "RequestStreaming") :
                (methodProto.getServerStreaming() ? "ResponseStreaming" : "");
    }

    private static MethodSpec newDelegatingCompletableMethodSpec(final String methodName,
                                                                 final String fieldName) {
        return newDelegatingMethodSpec(methodName, fieldName, Completable, null);
    }

    private static MethodSpec newDelegatingMethodSpec(final String methodName,
                                                      final String fieldName,
                                                      @Nullable final ClassName returnClass,
                                                      @Nullable final ClassName thrownClass) {
        final MethodSpec.Builder methodSpecBuilder = methodBuilder(methodName)
                .addModifiers(PUBLIC)
                .addAnnotation(Override.class)
                .addStatement("$L$L.$L()", (returnClass != null ? "return " : ""), fieldName, methodName);

        if (returnClass != null) {
            methodSpecBuilder.returns(returnClass);
        }
        if (thrownClass != null) {
            methodSpecBuilder.addException(thrownClass);
        }

        return methodSpecBuilder.build();
    }

    private static MethodSpec newDelegatingCompletableToBlockingMethodSpec(final String blockingMethodName,
                                                                           final String completableMethodName,
                                                                           final String fieldName) {
        return methodBuilder(blockingMethodName)
                .addModifiers(PUBLIC)
                .addAnnotation(Override.class)
                .addException(Exception.class)
                .addStatement("$L.$L().toFuture().get()", fieldName, completableMethodName)
                .build();
    }
}

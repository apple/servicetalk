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
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static com.squareup.javapoet.TypeSpec.enumBuilder;
import static com.squareup.javapoet.TypeSpec.interfaceBuilder;
import static io.servicetalk.grpc.protoc.StringUtils.sanitizeIdentifier;
import static io.servicetalk.grpc.protoc.Types.AllGrpcRoutes;
import static io.servicetalk.grpc.protoc.Types.AsyncCloseable;
import static io.servicetalk.grpc.protoc.Types.BlockingGrpcService;
import static io.servicetalk.grpc.protoc.Types.BlockingIterable;
import static io.servicetalk.grpc.protoc.Types.Completable;
import static io.servicetalk.grpc.protoc.Types.DefaultGrpcClientMetadata;
import static io.servicetalk.grpc.protoc.Types.GrpcExecutionStrategy;
import static io.servicetalk.grpc.protoc.Types.GrpcPayloadWriter;
import static io.servicetalk.grpc.protoc.Types.GrpcRoutes;
import static io.servicetalk.grpc.protoc.Types.GrpcSerializationProvider;
import static io.servicetalk.grpc.protoc.Types.GrpcService;
import static io.servicetalk.grpc.protoc.Types.GrpcServiceContext;
import static io.servicetalk.grpc.protoc.Types.GrpcServiceFactory;
import static io.servicetalk.grpc.protoc.Types.GrpcServiceFilterFactory;
import static io.servicetalk.grpc.protoc.Types.ProtoBufSerializationProviderBuilder;
import static io.servicetalk.grpc.protoc.Types.Publisher;
import static io.servicetalk.grpc.protoc.Types.RequestStreamingRoute;
import static io.servicetalk.grpc.protoc.Types.ResponseStreamingRoute;
import static io.servicetalk.grpc.protoc.Types.Route;
import static io.servicetalk.grpc.protoc.Types.Single;
import static io.servicetalk.grpc.protoc.Types.StreamingRoute;
import static io.servicetalk.grpc.protoc.Words.Blocking;
import static io.servicetalk.grpc.protoc.Words.Builder;
import static io.servicetalk.grpc.protoc.Words.Factory;
import static io.servicetalk.grpc.protoc.Words.Metadata;
import static io.servicetalk.grpc.protoc.Words.Rpc;
import static io.servicetalk.grpc.protoc.Words.append;
import static io.servicetalk.grpc.protoc.Words.appendServiceFilter;
import static io.servicetalk.grpc.protoc.Words.builder;
import static io.servicetalk.grpc.protoc.Words.closeAsync;
import static io.servicetalk.grpc.protoc.Words.closeAsyncGracefully;
import static io.servicetalk.grpc.protoc.Words.closeable;
import static io.servicetalk.grpc.protoc.Words.ctx;
import static io.servicetalk.grpc.protoc.Words.delegate;
import static io.servicetalk.grpc.protoc.Words.existing;
import static io.servicetalk.grpc.protoc.Words.filterFactory;
import static io.servicetalk.grpc.protoc.Words.path;
import static io.servicetalk.grpc.protoc.Words.request;
import static io.servicetalk.grpc.protoc.Words.routes;
import static io.servicetalk.grpc.protoc.Words.rpc;
import static io.servicetalk.grpc.protoc.Words.serializationProvider;
import static io.servicetalk.grpc.protoc.Words.service;
import static io.servicetalk.grpc.protoc.Words.strategy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.concat;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.DEFAULT;
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
        ClassName rpcPathsEnumClass;
        List<RpcInterface> rpcInterfaces;
        ClassName serviceClass;
        ClassName blockingServiceClass;
        ClassName serviceFilterClass;
        ClassName serviceFilterFactoryClass;
        List<ClientMetaData> clientMetaDatas;

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

        addRpcPathsEnum(state, serviceClassBuilder);
        addSerializationProviderInit(state, serviceClassBuilder);
        addRpcInterfaces(state, serviceClassBuilder);
        addServiceInterfaces(state, serviceClassBuilder);
        addServiceFilter(state, serviceClassBuilder);
        addServiceFilterFactory(state, serviceClassBuilder);
        addServiceFactory(state, serviceClassBuilder);

        addClientMetadata(state, serviceClassBuilder);

        // TODO(david) generate FilterableClient / Client / BlockingClient
        // TODO(david) generate ClientFilter
        // TODO(david) generate ClientFilterFactory
        // TODO(david) generate ClientFactory
    }

    private void addRpcPathsEnum(final State state, final TypeSpec.Builder serviceClassBuilder) {
        final TypeSpec.Builder rpcPathsEnumBuilder = enumBuilder("RpcPaths")
                .addModifiers(PUBLIC)
                .addField(String.class, path, PRIVATE, FINAL)
                .addMethod(constructorBuilder()
                        .addParameter(String.class, path)
                        .addStatement("this.$L = $L", path, path)
                        .build())
                .addMethod(methodBuilder(path)
                        .addModifiers(PUBLIC)
                        .returns(String.class)
                        .addStatement("return $L", path)
                        .build());

        state.serviceProto.getMethodList().forEach(methodProto ->
                rpcPathsEnumBuilder.addEnumConstant(routeName(methodProto),
                        anonymousClassBuilder("$S", context.methodPath(state.serviceProto, methodProto)).build()));

        final TypeSpec enumSpec = rpcPathsEnumBuilder.build();
        state.rpcPathsEnumClass = ClassName.bestGuess(enumSpec.name);
        serviceClassBuilder.addType(enumSpec);
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

    private void addRpcInterfaces(final State state, final TypeSpec.Builder serviceClassBuilder) {
        state.rpcInterfaces = new ArrayList<>(2 * state.serviceProto.getMethodCount());

        state.serviceProto.getMethodList().forEach(methodProto -> Stream.of(false, true).forEach(blocking -> {
            final String name = (blocking ? Blocking : "") + sanitizeIdentifier(methodProto.getName(), false) + Rpc;

            final TypeSpec.Builder interfaceSpecBuilder = interfaceBuilder(name)
                    .addModifiers(PUBLIC)
                    .addMethod(newRpcMethodSpecBuilder(methodProto, blocking, (__, b) -> b.addModifiers(ABSTRACT)));

            if (methodProto.hasOptions() && methodProto.getOptions().getDeprecated()) {
                interfaceSpecBuilder.addAnnotation(Deprecated.class);
            }

            final TypeSpec interfaceSpec = interfaceSpecBuilder.build();
            state.rpcInterfaces.add(new RpcInterface(methodProto, blocking, ClassName.bestGuess(name)));
            serviceClassBuilder.addType(interfaceSpec);
        }));
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
        state.serviceFilterClass = state.serviceClass.peerClass(state.serviceClass.simpleName() + "Filter");

        final TypeSpec.Builder classSpecBuilder = classBuilder(state.serviceFilterClass)
                .addModifiers(PUBLIC, STATIC)
                .addSuperinterface(state.serviceClass)
                .addField(state.serviceClass, delegate, PRIVATE, FINAL)
                .addMethod(constructorBuilder()
                        .addModifiers(PROTECTED)
                        .addParameter(state.serviceClass, delegate, FINAL)
                        .addStatement("this.$L = $L", delegate, delegate)
                        .build())
                .addMethod(methodBuilder(delegate)
                        .addModifiers(PROTECTED)
                        .returns(state.serviceClass)
                        .addStatement("return $L", delegate)
                        .build())
                .addMethod(methodBuilder(closeAsync)
                        .addModifiers(PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(Completable)
                        .addStatement("return $L.$L()", delegate, closeAsync)
                        .build())
                .addMethod(methodBuilder(closeAsyncGracefully)
                        .addModifiers(PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(Completable)
                        .addStatement("return $L.$L()", delegate, closeAsyncGracefully)
                        .build());

        state.serviceProto.getMethodList().forEach(methodProto ->
                classSpecBuilder.addMethod(newRpcMethodSpecBuilder(methodProto, false,
                        (name, builder) ->
                                builder.addAnnotation(Override.class)
                                        .addStatement("return $L.$L($L, $L)", delegate, name, ctx, request))));

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
        final ClassName serviceFactoryClass = state.serviceClass.peerClass(state.serviceClass.simpleName() + Factory);
        final ClassName builderClass = serviceFactoryClass.nestedClass(Builder);
        final ClassName serviceFromRoutesClass = builderClass.nestedClass(state.serviceClass.simpleName() +
                "FromRoutes");

        // TODO(nitesh): Warn for path override and Validate all paths are defined.
        final TypeSpec.Builder serviceBuilderSpecBuilder = classBuilder(Builder)
                .addModifiers(PUBLIC, STATIC, FINAL)
                .superclass(ParameterizedTypeName.get(GrpcRoutes, state.serviceClass))
                .addType(newServiceFromRoutesSpec(serviceFromRoutesClass, state.rpcPathsEnumClass, state.serviceClass,
                        state.serviceProto))
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

        state.rpcInterfaces.forEach(rpcInterfaceMeta -> {
            final ClassName inClassName = messageTypesMap.get(rpcInterfaceMeta.methodProto.getInputType());
            final ClassName outClassName = messageTypesMap.get(rpcInterfaceMeta.methodProto.getOutputType());
            final String routeName = routeName(rpcInterfaceMeta.methodProto);
            final String methodName = routeName + (rpcInterfaceMeta.blocking ? Blocking : "");
            final String addRouteMethodName = addRouteMethodName(rpcInterfaceMeta.methodProto,
                    rpcInterfaceMeta.blocking);

            serviceBuilderSpecBuilder
                    .addMethod(methodBuilder(methodName)
                            .addModifiers(PUBLIC)
                            .addParameter(rpcInterfaceMeta.className, rpc, FINAL)
                            .returns(builderClass)
                            .addStatement("$L($T.$L.path(), $L::$L, $T.class, $T.class, $L)", addRouteMethodName,
                                    state.rpcPathsEnumClass, routeName, rpc, routeName, inClassName, outClassName,
                                    serializationProvider)
                            .addStatement("return this")
                            .build())
                    .addMethod(methodBuilder(methodName)
                            .addModifiers(PUBLIC)
                            .addParameter(GrpcExecutionStrategy, strategy, FINAL)
                            .addParameter(rpcInterfaceMeta.className, rpc, FINAL)
                            .returns(builderClass)
                            .addStatement("$L($T.$L.path(), $L, $L::$L, $T.class, $T.class, $L)", addRouteMethodName,
                                    state.rpcPathsEnumClass, routeName, strategy, rpc, routeName, inClassName,
                                    outClassName, serializationProvider)
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

        final TypeSpec.Builder serviceClassSpecBuilder = classBuilder(serviceFactoryClass)
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
                        .addParameter(state.blockingServiceClass, service, FINAL)
                        .addStatement("super(new $T().$L)", builderClass,
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
                        .addParameter(state.serviceFilterFactoryClass, filterFactory, FINAL)
                        .addStatement("super.$L($L)", appendServiceFilter, filterFactory)
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

        final TypeSpec serviceClassSpec = serviceClassSpecBuilder.build();
        serviceClassBuilder.addType(serviceClassSpec);
    }

    private void addClientMetadata(final State state, final TypeSpec.Builder serviceClassBuilder) {
        state.clientMetaDatas = new ArrayList<>(state.serviceProto.getMethodCount());

        state.serviceProto.getMethodList().forEach(methodProto -> {
            final String name = sanitizeIdentifier(methodProto.getName(), false) + Metadata;

            final TypeSpec classSpec = classBuilder(name)
                    .addModifiers(PUBLIC, STATIC, FINAL)
                    .superclass(DefaultGrpcClientMetadata)
                    .addMethod(constructorBuilder()
                            .addModifiers(PUBLIC)
                            .addStatement("super($T.$L.$L())", state.rpcPathsEnumClass, routeName(methodProto), path)
                            .build())
                    .build();

            state.clientMetaDatas.add(new ClientMetaData(methodProto, ClassName.bestGuess(name)));
            serviceClassBuilder.addType(classSpec);
        });
    }

    private MethodSpec newRpcMethodSpecBuilder(final MethodDescriptorProto methodProto,
                                               final boolean blocking,
                                               final BiFunction<String, MethodSpec.Builder, MethodSpec.Builder>
                                                       methodBuilderCustomizer) {

        final ClassName inClassName = messageTypesMap.get(methodProto.getInputType());
        final ClassName outClassName = messageTypesMap.get(methodProto.getOutputType());

        final String name = routeName(methodProto);

        final MethodSpec.Builder methodSpecBuilder = methodBuilder(name)
                .addModifiers(PUBLIC)
                .addParameter(GrpcServiceContext, ctx, FINAL);

        if (blocking) {
            methodSpecBuilder.addException(Exception.class);

            if (methodProto.getClientStreaming()) {
                methodSpecBuilder.addParameter(ParameterizedTypeName.get(BlockingIterable, inClassName), request,
                        FINAL);
            } else {
                methodSpecBuilder.addParameter(inClassName, request, FINAL);
            }

            if (methodProto.getServerStreaming()) {
                methodSpecBuilder.addParameter(ParameterizedTypeName.get(GrpcPayloadWriter, outClassName),
                        "responseWriter", FINAL);
            } else {
                methodSpecBuilder.returns(outClassName);
            }
        } else {
            if (methodProto.getClientStreaming()) {
                methodSpecBuilder.addParameter(ParameterizedTypeName.get(Publisher, inClassName), request, FINAL);
            } else {
                methodSpecBuilder.addParameter(inClassName, request, FINAL);
            }

            if (methodProto.getServerStreaming()) {
                methodSpecBuilder.returns(ParameterizedTypeName.get(Publisher, outClassName));
            } else {
                methodSpecBuilder.returns(ParameterizedTypeName.get(Single, outClassName));
            }
        }

        return methodBuilderCustomizer.apply(name, methodSpecBuilder).build();
    }

    private TypeSpec newServiceInterfaceSpec(final State state, final boolean blocking) {
        final String name = (blocking ? Blocking : "") + sanitizeIdentifier(state.serviceProto.getName(), false) +
                "Service";

        final TypeSpec.Builder interfaceSpecBuilder = interfaceBuilder(name)
                .addModifiers(PUBLIC)
                .addSuperinterface(blocking ? BlockingGrpcService : GrpcService);

        state.rpcInterfaces.stream()
                .filter(e -> e.blocking == blocking)
                .map(e -> e.className)
                .forEach(interfaceSpecBuilder::addSuperinterface);

        if (blocking) {
            interfaceSpecBuilder
                    .addMethod(methodBuilder("close")
                            .addModifiers(DEFAULT, PUBLIC)
                            .addAnnotation(Override.class)
                            .addComment("noop")
                            .build());
        } else {
            interfaceSpecBuilder
                    .addMethod(methodBuilder(closeAsync)
                            .addModifiers(DEFAULT, PUBLIC)
                            .addAnnotation(Override.class)
                            .returns(Completable)
                            .addStatement("return $T.completed()", Completable)
                            .build());
        }

        return interfaceSpecBuilder.build();
    }

    private TypeSpec newServiceFromRoutesSpec(final ClassName serviceFromRoutesClass, final ClassName routesEnumClass,
                                              final ClassName serviceClass, final ServiceDescriptorProto serviceProto) {

        final TypeSpec.Builder serviceFromRoutesSpecBuilder = classBuilder(serviceFromRoutesClass)
                .addModifiers(PRIVATE, STATIC, FINAL)
                .addSuperinterface(serviceClass)
                .addField(AsyncCloseable, closeable, PRIVATE, FINAL);

        final MethodSpec.Builder serviceFromRoutesConstructorBuilder = constructorBuilder()
                .addParameter(AllGrpcRoutes, routes, FINAL)
                .addStatement("$L = $L", closeable, routes);

        serviceProto.getMethodList().forEach(methodProto -> {
            final ClassName inClassName = messageTypesMap.get(methodProto.getInputType());
            final ClassName outClassName = messageTypesMap.get(methodProto.getOutputType());
            final String routeName = routeName(methodProto);

            serviceFromRoutesSpecBuilder.addField(ParameterizedTypeName.get(routeInterfaceClass(methodProto),
                    inClassName, outClassName), routeName, PRIVATE, FINAL);

            serviceFromRoutesConstructorBuilder.addStatement("$L = $L.$L($T.$L.path())", routeName, routes,
                    routeFactoryMethodName(methodProto), routesEnumClass, routeName);

            serviceFromRoutesSpecBuilder.addMethod(newRpcMethodSpecBuilder(methodProto, false,
                    (name, builder) ->
                            builder.addAnnotation(Override.class)
                                    .addStatement("return $L.handle($L, $L)", routeName, ctx, request)));
        });

        serviceFromRoutesSpecBuilder
                .addMethod(serviceFromRoutesConstructorBuilder.build())
                .addMethod(methodBuilder(closeAsync)
                        .addModifiers(PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(Completable)
                        .addStatement("return $L.$L()", closeable, closeAsync)
                        .build())
                .addMethod(methodBuilder(closeAsyncGracefully)
                        .addModifiers(PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(Completable)
                        .addStatement("return $L.$L()", closeable, closeAsyncGracefully)
                        .build());

        return serviceFromRoutesSpecBuilder.build();
    }

    private static String routeName(final MethodDescriptorProto methodProto) {
        return sanitizeIdentifier(methodProto.getName(), true);
    }

    private static ClassName routeInterfaceClass(final MethodDescriptorProto methodProto) {
        return methodProto.getClientStreaming() ?
                (methodProto.getServerStreaming() ? StreamingRoute : RequestStreamingRoute) :
                (methodProto.getServerStreaming() ? ResponseStreamingRoute : Route);
    }

    private static String routeFactoryMethodName(final MethodDescriptorProto methodProto) {
        return (methodProto.getClientStreaming() ?
                (methodProto.getServerStreaming() ? "streamingR" : "requestStreamingR") :
                (methodProto.getServerStreaming() ? "responseStreamingR" : "r")) +
                "outeFor";
    }

    private static String addRouteMethodName(final MethodDescriptorProto methodProto, final boolean blocking) {
        return "add" +
                (blocking ? Blocking : "") +
                (methodProto.getClientStreaming() ?
                        (methodProto.getServerStreaming() ? "Streaming" : "RequestStreaming") :
                        (methodProto.getServerStreaming() ? "ResponseStreaming" : "")) +
                "Route";
    }

    private static String serviceFactoryBuilderInitChain(final ServiceDescriptorProto serviceProto,
                                                         final boolean blocking) {
        return serviceProto.getMethodList().stream()
                .map(methodProto -> routeName(methodProto) + (blocking ? Blocking : "") + '(' + service + ')')
                .collect(joining("."));
    }
}

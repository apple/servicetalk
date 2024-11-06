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

import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import com.squareup.javapoet.ClassName;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest.parseFrom;
import static com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL;
import static io.servicetalk.grpc.protoc.StringUtils.parseOptions;
import static java.lang.Boolean.parseBoolean;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * This class will be invoked by the protoc compiler to create Java ServiceTalk source classes. It implements the
 * <a href="https://developers.google.com/protocol-buffers/docs/reference/cpp/google.protobuf.compiler.plugin.pb">
 * plugin.proto</a>
 * interface to produce ServiceTalk code corresponding to .proto service definitions.
 */
public final class Main {

    /**
     * Supports an option to append a postfix to service type names generated by this protoc plugin. This is useful
     * to avoid conflicts between multiple gRPC implementations generating code for the same .proto file in the same
     * gradle or maven project.
     * <p>
     * Gradle:
     * <pre>{@code
     * task.plugins {
     *   servicetalk_grpc {
     *     option 'typeNameSuffix=Foo'
     *   }
     * }
     * }</pre>
     * <p>
     * Maven:
     * <pre>{@code
     * <protocPlugin>
     *   <args>
     *     <arg>typeNameSuffix=Foo</arg>
     *   </args>
     * </protocPlugin>
     * }</pre>
     */
    private static final String TYPE_NAME_SUFFIX_OPTION = "typeNameSuffix";
    /**
     * Supports an option to disable javaDoc generation.
     * <p>
     * Gradle:
     * <pre>
     * task.plugins {
     *   servicetalk_grpc {
     *     option 'javaDocs=false'
     *   }
     * }
     * </pre>
     * <p>
     * Maven:
     * <pre>{@code
     * <protocPlugin>
     *   <args>
     *     <arg>javaDocs=false</arg>
     *   </args>
     * </protocPlugin>
     * }</pre>
     */
    private static final String PRINT_JAVA_DOCS_OPTION = "javaDocs";
    /**
     * Supports an option to disable generating calls to deprecated libraries.
     * <p>
     * Gradle:
     * <pre>
     * task.plugins {
     *   servicetalk_grpc {
     *     option 'skipDeprecated=true'
     *   }
     * }
     * </pre>
     * <p>
     * Maven:
     * <pre>{@code
     * <protocPlugin>
     *   <args>
     *     <arg>skipDeprecated=true</arg>
     *   </args>
     * </protocPlugin>
     * }</pre>
     */
    private static final String SKIP_DEPRECATED_CODE = "skipDeprecated";
    private Main() {
        // no instances
    }

    /**
     * Program entry point. It is expected a {@link CodeGeneratorRequest} will be read from stdin,
     * and a {@link CodeGeneratorResponse} will be written to stdout.
     *
     * @param args the program arguments
     * @throws IOException if an exception occurs while parsing input from stdin
     */
    public static void main(final String... args) throws IOException {
        safeGenerate(parseFrom(System.in), args).writeTo(System.out);
    }

    /**
     * Generate response from request while ensuring that any exceptions thrown
     * during generation are transformed in to an appropriate error response.
     *
     * @param request The generation request
     * @param args command-line args passed in from the generator plugin
     * @return The generation response
     */
    private static CodeGeneratorResponse safeGenerate(final CodeGeneratorRequest request,
                                                      final String... args) {
        try {
            return generate(request, argsToOptions(args));
        } catch (final Throwable t) {
            final StringWriter sw = new StringWriter(1024);
            sw.append("ServiceTalk code generation failed: ");
            try (PrintWriter pw = new PrintWriter(sw)) {
                t.printStackTrace(pw);
            }
            return CodeGeneratorResponse.newBuilder().setError(sw.toString()).build();
        }
    }

    /**
     * Generate response from code generation request.
     *
     * @param request The code generation request
     * @param optionsMap options map supplied from plugins on the command line
     * @return The code generation response
     */
    private static CodeGeneratorResponse generate(final CodeGeneratorRequest request,
                                                  final Map<String, String> optionsMap) {
        final CodeGeneratorResponse.Builder responseBuilder = CodeGeneratorResponse.newBuilder()
                // Optional support only impacts "message" types, this plugin only targets "service" types.
                // However the protoc compiler will fail if the feature isn't marked as supported.
                .setSupportedFeatures(FEATURE_PROTO3_OPTIONAL.getNumber());

        final Set<String> filesToGenerate = new HashSet<>(request.getFileToGenerateList());

        if (request.hasParameter()) {
            optionsMap.putAll(parseOptions(request.getParameter()));
        }
        final String typeSuffixValue = optionsMap.get(TYPE_NAME_SUFFIX_OPTION);
        final boolean printJavaDocs = parseBoolean(optionsMap.getOrDefault(PRINT_JAVA_DOCS_OPTION, "true"));
        final boolean skipDeprecated = parseBoolean(optionsMap.getOrDefault(SKIP_DEPRECATED_CODE, "false"));

        final List<FileDescriptor> fileDescriptors = request.getProtoFileList().stream()
                .map(protoFile -> new FileDescriptor(protoFile, typeSuffixValue)).collect(toList());

        final Map<String, ClassName> messageTypesMap = fileDescriptors.stream()
                .map(FileDescriptor::messageTypesMap)
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(toMap(Entry::getKey, Entry::getValue));

        for (FileDescriptor f : fileDescriptors) {
            if (filesToGenerate.contains(f.protoFileName())) {
                final Generator generator = new Generator(
                        f, messageTypesMap, printJavaDocs, skipDeprecated, f.sourceCodeInfo());
                List<ServiceDescriptorProto> serviceDescriptorProtoList = f.protoServices();
                for (int i = 0; i < serviceDescriptorProtoList.size(); ++i) {
                    ServiceDescriptorProto serviceDescriptor = serviceDescriptorProtoList.get(i);
                    generator.generate(f, serviceDescriptor, i);
                }
                f.writeTo(responseBuilder);
            }
        }

        return responseBuilder.build();
    }

    /**
     * Helper method to turn command line arguments into option key/value pairs.
     *
     * @param args the arguments list, can be empty.
     * @return a (potentially empty) map of arguments.
     */
    private static Map<String, String> argsToOptions(final String... args) {
        final Map<String, String> optionsMap = new HashMap<>();
        for (String arg : args) {
            String[] kv = arg.split("=");
            if (kv.length != 2) {
                throw new IllegalArgumentException("Command line argument must be of shape: key=value " +
                        "(is: " + arg + ")");
            }
            optionsMap.put(kv[0], kv[1]);
        }
        return optionsMap;
    }
}

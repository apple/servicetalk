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

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.File;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static io.servicetalk.grpc.protoc.StringUtils.isNotNullNorEmpty;
import static io.servicetalk.grpc.protoc.StringUtils.sanitizeIdentifier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

final class FileDescriptor implements GenerationContext {
    private static final String GENERATED_BY_COMMENT = "Generated by ServiceTalk proto compiler";

    private final FileDescriptorProto protoFile;
    private final String sanitizedProtoFileName;
    @Nullable
    private final String protoPackageName;
    private final boolean deprecated;
    private final boolean multipleClassFiles;
    @Nullable
    private final String javaPackageName;
    @Nullable
    private final String outerClassName;
    private final List<TypeSpec.Builder> serviceClassBuilders;
    @Nullable
    private Set<String> reservedJavaTypeName;

    FileDescriptor(final FileDescriptorProto protoFile) {
        this.protoFile = protoFile;
        sanitizedProtoFileName = sanitizeFileName(protoFile.getName());
        protoPackageName = protoFile.hasPackage() ? protoFile.getPackage() : null;

        if (protoFile.hasOptions()) {
            final FileOptions fileOptions = protoFile.getOptions();
            deprecated = fileOptions.hasDeprecated() && fileOptions.getDeprecated();
            multipleClassFiles = fileOptions.hasJavaMultipleFiles() && fileOptions.getJavaMultipleFiles();
            javaPackageName = fileOptions.hasJavaPackage() ? fileOptions.getJavaPackage() : null;
            outerClassName = fileOptions.hasJavaOuterClassname() ? fileOptions.getJavaOuterClassname() : null;
        } else {
            deprecated = false;
            multipleClassFiles = false;
            javaPackageName = null;
            outerClassName = null;
        }

        serviceClassBuilders = new ArrayList<>(protoFile.getServiceCount());
    }

    String protoFileName() {
        return protoFile.getName();
    }

    List<ServiceDescriptorProto> protoServices() {
        return protoFile.getServiceList();
    }

    Map<String, ClassName> messageTypesMap() {
        final Map<String, ClassName> messageTypesMap = new HashMap<>(protoFile.getMessageTypeCount());
        addMessageTypes(protoFile.getMessageTypeList(), protoPackageName != null ? '.' + protoPackageName : null,
                multipleClassFiles ? javaPackageName() : javaPackageName() + '.' + outerJavaClassName(),
                messageTypesMap);
        return messageTypesMap;
    }

    private void addMessageTypes(final List<DescriptorProto> messageTypes,
                                 @Nullable String parentProtoScope,
                                 @Nullable String parentJavaScope,
                                 final Map<String, ClassName> messageTypesMap) {

        messageTypes.forEach(t -> {
            final String protoTypeName = (parentProtoScope != null ? parentProtoScope : "") + '.' + t.getName();
            final String javaClassName = parentJavaScope + '.' + t.getName();
            messageTypesMap.put(protoTypeName, ClassName.bestGuess(javaClassName));

            addMessageTypes(t.getNestedTypeList(), protoTypeName, javaClassName, messageTypesMap);
        });
    }

    @Override
    public String deconflictJavaTypeName(final String name) {
        if (reservedJavaTypeName == null) {
            reservedJavaTypeName = new HashSet<>();
            reservedJavaTypeName.add(outerJavaClassName());
        }

        if (reservedJavaTypeName.add(name)) {
            return name;
        }

        int i = 0;
        String uniqueName;
        do {
            uniqueName = name + i;
            i++;
        } while (!reservedJavaTypeName.add(uniqueName));

        return uniqueName;
    }

    @Override
    public TypeSpec.Builder newServiceClassBuilder(final ServiceDescriptorProto serviceProto) {
        final String className = deconflictJavaTypeName(sanitizeClassName(serviceProto.getName()));

        final TypeSpec.Builder builder = TypeSpec.classBuilder(className)
                .addModifiers(PUBLIC, FINAL)
                .addMethod(constructorBuilder()
                        .addModifiers(PRIVATE)
                        .addComment("no instances")
                        .build());

        if (deprecated || serviceProto.hasOptions() && serviceProto.getOptions().hasDeprecated() &&
                serviceProto.getOptions().getDeprecated()) {
            builder.addAnnotation(Deprecated.class);
        }

        serviceClassBuilders.add(builder);
        return builder;
    }

    @Override
    public String methodPath(final ServiceDescriptorProto serviceProto, final MethodDescriptorProto methodProto) {
        final StringBuilder sb = new StringBuilder(128).append('/');
        if (isNotNullNorEmpty(protoPackageName)) {
            sb.append(protoPackageName).append('.');
        }
        sb.append(serviceProto.getName()).append('/').append(methodProto.getName());
        return sb.toString();
    }

    void writeTo(final CodeGeneratorResponse.Builder responseBuilder) {
        if (serviceClassBuilders.isEmpty()) {
            return;
        }

        if (!multipleClassFiles) {
            // All source code should be put into 1 file, use the file that is generated by protoc,
            // which is done by writing to a .java file whose name is calculated to match the one that protoc
            // will create (i.e. this file name is not provided in CodeGeneratorRequest)
            final String fileName = calculateFileName(javaPackageName(), outerJavaClassName());

            insertSingleFileContent("// " + GENERATED_BY_COMMENT, fileName, responseBuilder);
            for (final TypeSpec.Builder builder : serviceClassBuilders) {
                insertSingleFileContent(builder.addModifiers(STATIC).build().toString(), fileName, responseBuilder);
            }
            return;
        }

        // write each service to its own file
        final String packageName = javaPackageName();
        for (final TypeSpec.Builder builder : serviceClassBuilders) {
            final TypeSpec serviceType = builder.build();
            final File.Builder fileBuilder = File.newBuilder();
            fileBuilder.setName(calculateFileName(packageName, serviceType.name));

            final JavaFile javaFile = JavaFile.builder(packageName, serviceType)
                    .addFileComment(GENERATED_BY_COMMENT)
                    .build();

            fileBuilder.setContent(javaFile.toString());
            responseBuilder.addFile(fileBuilder.build());
        }
    }

    private static void insertSingleFileContent(final String content, String fileName,
                                                final CodeGeneratorResponse.Builder responseBuilder) {
        final File.Builder fileBuilder = File.newBuilder();
        fileBuilder.setName(fileName);
        fileBuilder.setInsertionPoint("outer_class_scope");
        fileBuilder.setContent(content + "\n");
        responseBuilder.addFile(fileBuilder.build());
    }

    private String outerJavaClassName() {
        return isNotNullNorEmpty(outerClassName) ? sanitizeClassName(outerClassName) :
                sanitizeClassName(sanitizedProtoFileName);
    }

    private String javaPackageName() {
        return isNotNullNorEmpty(javaPackageName) ? javaPackageName :
                isNotNullNorEmpty(protoPackageName) ? protoPackageName : sanitizeClassName(sanitizedProtoFileName);
    }

    private static String sanitizeFileName(final String v) {
        int i = v.lastIndexOf('/');
        final int j = v.lastIndexOf('.');
        if (i != -1 && j != -1) {
            if (++i >= v.length()) {
                throw new IllegalArgumentException("Illegal file name: " + v);
            }
            return v.substring(i, j);
        } else if (j != -1) {
            return v.substring(0, j);
        }
        return v;
    }

    private static String sanitizeClassName(final String v) {
        return sanitizeIdentifier(v, false);
    }

    private static String calculateFileName(final String packageName, final String className) {
        return packageName.replace('.', '/') + '/' + className + ".java";
    }
}

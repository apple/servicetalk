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
package io.servicetalk.grpc.protoc;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;
import com.google.protobuf.Descriptors.FieldDescriptor;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

final class DefaultServiceCommentsMap implements ServiceCommentsMap {
    private static final int SERVICE_PATH;
    private static final int METHOD_PATH;
    private final Map<Long, String> commentMap;

    static {
        int servicePath = -1;
        int methodPath = -1;
        FieldDescriptor serviceFieldDescriptor = FileDescriptorProto.getDescriptor().findFieldByName("service");
        if (serviceFieldDescriptor != null) {
            servicePath = serviceFieldDescriptor.getNumber();
            FieldDescriptor methodFieldDescriptor = ServiceDescriptorProto.getDescriptor().findFieldByName("method");
            if (methodFieldDescriptor != null) {
                methodPath = methodFieldDescriptor.getNumber();
            }
        }
        SERVICE_PATH = servicePath;
        METHOD_PATH = methodPath;
    }

    DefaultServiceCommentsMap(SourceCodeInfo sourceCodeInfo) {
        commentMap = new HashMap<>();
        for (SourceCodeInfo.Location location : sourceCodeInfo.getLocationList()) {
            if (location.hasLeadingComments() && location.getPathCount() == 4 &&
                    location.getPath(0) == SERVICE_PATH && location.getPath(2) == METHOD_PATH) {
                // location.getPath(1) - the service number in the file (0 based)
                // location.getPath(3) - the method number in the service (0 based)
                commentMap.put(combineIndex(location.getPath(1), location.getPath(3)), location.getLeadingComments());
            }
        }
    }

    @Nullable
    @Override
    public String getLeadingComments(final int serviceIndex, final int methodIndex) {
        return commentMap.get(combineIndex(serviceIndex, methodIndex));
    }

    @Override
    public String toString() {
        return commentMap.toString();
    }

    private static long combineIndex(final int serviceIndex, final int methodIndex) {
        return (((long) serviceIndex) << 32) | methodIndex;
    }
}

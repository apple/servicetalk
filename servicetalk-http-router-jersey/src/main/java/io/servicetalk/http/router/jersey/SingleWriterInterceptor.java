/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.jersey;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Single;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import javax.annotation.Nullable;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

/**
 * A {@link WriterInterceptor} that sets the generic {@link Type} of the response to the type of the {@link Single}
 * value in order for 3rd party {@link MessageBodyWriter}s to work properly (they do not know about {@link Single}).
 * Note that this is only done for {@link Single}s of any type except {@link Buffer}, since we do have a specific
 * {@link MessageBodyWriter} for {@link Buffer}s.
 *
 * @see BufferSingleMessageBodyReaderWriter
 */
final class SingleWriterInterceptor implements WriterInterceptor {
    @Override
    public void aroundWriteTo(final WriterInterceptorContext writerInterceptorCtx) throws IOException {
        if (isSingleOfAnythingButBuffer(writerInterceptorCtx.getGenericType())) {
            final ParameterizedType parameterizedType = (ParameterizedType) writerInterceptorCtx.getGenericType();
            writerInterceptorCtx.setGenericType(parameterizedType.getActualTypeArguments()[0]);
        }

        writerInterceptorCtx.proceed();
    }

    private static boolean isSingleOfAnythingButBuffer(@Nullable final Type entityType) {
        if (!(entityType instanceof ParameterizedType)) {
            return false;
        }
        final ParameterizedType parameterizedType = (ParameterizedType) entityType;
        if (!Single.class.isAssignableFrom((Class<?>) parameterizedType.getRawType())) {
            return false;
        }
        final Type[] typeArguments = parameterizedType.getActualTypeArguments();
        final Type singleType = typeArguments[0];
        return !(singleType instanceof Class) || !Buffer.class.isAssignableFrom((Class<?>) singleType);
    }
}

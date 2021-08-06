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
package io.servicetalk.data.jackson.jersey;

import io.servicetalk.serializer.api.SerializationException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.UNSUPPORTED_MEDIA_TYPE;
import static javax.ws.rs.core.Response.status;

final class SerializationExceptionMapper implements ExceptionMapper<SerializationException> {
    @Override
    public Response toResponse(final SerializationException e) {
        return status(isDueToBadUserData(e) ? UNSUPPORTED_MEDIA_TYPE : INTERNAL_SERVER_ERROR).build();
    }

    private static boolean isDueToBadUserData(final SerializationException e) {
        return e.getCause() instanceof JsonMappingException || e.getCause() instanceof JsonParseException;
    }
}

/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.serializer.api.SerializationException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.servicetalk.http.api.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JacksonSerializationExceptionMapperTest {

    private class Pojo {
        protected int i;
    }

    @Test
    void mapsInvalidJsonExceptionsTo415() throws Exception {
        try {
            new ObjectMapper().readValue("{bad json}".getBytes(StandardCharsets.UTF_8), Object.class);
            Assertions.fail("shouldn't get here.");
        } catch (JsonParseException ex) {
            assertUnderlyingException(UNSUPPORTED_MEDIA_TYPE, ex);
        }
    }

    @Test
    void mapsParsingStructureExceptionsTo415() throws Exception {
        try {
            new ObjectMapper().readValue("{\"i\": \"foo\"}".getBytes(StandardCharsets.UTF_8), Pojo.class);
            Assertions.fail("shouldn't get here.");
        } catch (JsonMappingException ex) {
            assertUnderlyingException(UNSUPPORTED_MEDIA_TYPE, ex);
        }
    }

    @Test
    void jsonMappingExceptionWithoutProcessorConvertsTo415() {
        JsonMappingException ex = new JsonMappingException("");
        assertNull(ex.getProcessor());
        assertUnderlyingException(UNSUPPORTED_MEDIA_TYPE, ex);
    }

    @Test
    void mapsSerializationExceptionsTo500() {
        try {
            new ObjectMapper().writeValueAsString(new Object());
            Assertions.fail("shouldn't get here.");
        } catch (Exception ex) {
            assertUnderlyingException(INTERNAL_SERVER_ERROR, ex);
        }
    }

    void assertUnderlyingException(HttpResponseStatus status, Throwable ex) {
        int observed = new JacksonSerializationExceptionMapper().toResponse(new SerializationException(ex)).getStatus();
        assertEquals(status.code(), observed);
    }
}

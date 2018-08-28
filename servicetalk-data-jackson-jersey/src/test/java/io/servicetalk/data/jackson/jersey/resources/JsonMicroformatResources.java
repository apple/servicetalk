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
package io.servicetalk.data.jackson.jersey.resources;

import io.servicetalk.concurrent.api.Single;

import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import static io.servicetalk.data.jackson.jersey.resources.JsonMicroformatResources.PATH;
import static java.util.Collections.singletonMap;

@Path(PATH)
public class JsonMicroformatResources {
    public static final String PATH = "/json-microformat";
    public static final String APPLICATION_VND_INPUT_JSON = "application/vnd.input+json";
    public static final String APPLICATION_VND_OUTPUT_JSON = "application/vnd.output+json";

    @Consumes(APPLICATION_VND_INPUT_JSON)
    @Produces(APPLICATION_VND_OUTPUT_JSON)
    @POST
    public Single<Map<String, Object>> postJson(final Single<Map<String, Object>> single) {
        return single.map(m -> singletonMap("got", m));
    }
}

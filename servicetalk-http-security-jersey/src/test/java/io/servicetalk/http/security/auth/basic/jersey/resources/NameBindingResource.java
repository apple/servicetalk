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
package io.servicetalk.http.security.auth.basic.jersey.resources;

import io.servicetalk.http.security.auth.basic.jersey.BasicAuthenticated;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import static io.servicetalk.http.security.auth.basic.jersey.resources.NameBindingResource.PATH;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(PATH)
public class NameBindingResource {
    public static final String PATH = "/name-binding";

    @BasicAuthenticated
    @Produces(APPLICATION_JSON)
    @Path("/security-context")
    @GET
    public SecurityContext securityContext(@Context final SecurityContext securityContext) {
        return securityContext;
    }
}

/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.jaxrs;

import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import java.util.HashMap;
import java.util.Map;

import static io.servicetalk.http.router.jaxrs.ParameterUtil.castTo;

public class PathParameter extends Parameter {

    private final PathTemplateDelegate pathTemplate;

    public PathParameter(String name, Class<?> type, final PathTemplateDelegate pathTemplate) {
        super(name, type);
        this.pathTemplate = pathTemplate;
    }

    @Override
    public Object get(final HttpServiceContext ctx, final StreamingHttpRequest request,
                      final StreamingHttpResponseFactory responseFactory) {
        final Map<String, String> parameters = new HashMap<>(pathTemplate.getNumberOfTemplateVariables());
        if (!pathTemplate.match(request.path(), parameters)) {
            return null;
        }
        return castTo(type, parameters.get(name));
    }
}

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

import org.glassfish.jersey.uri.PathTemplate;

import java.util.Map;
import java.util.regex.Pattern;

public class PathTemplateDelegate {
    private final PathTemplate pathTemplate;

    public PathTemplateDelegate(final String path) {
        this.pathTemplate = new PathTemplate(path);
    }

    public Pattern getRegex() {
        return Pattern.compile(pathTemplate.getPattern().getRegex());
    }

    public int getNumberOfTemplateVariables() {
        return pathTemplate.getNumberOfTemplateVariables();
    }

    public boolean match(final String path, final Map<String, String> parameters) {
        return pathTemplate.match(path, parameters);
    }
}

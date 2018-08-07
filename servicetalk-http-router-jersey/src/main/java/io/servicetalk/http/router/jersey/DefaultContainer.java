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

import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.Container;

final class DefaultContainer implements Container {
    private volatile ApplicationHandler handler;

    DefaultContainer(final ApplicationHandler handler) {
        this.handler = handler;
        handler.onStartup(this);
    }

    @Override
    public ResourceConfig getConfiguration() {
        return handler.getConfiguration();
    }

    @Override
    public ApplicationHandler getApplicationHandler() {
        return handler;
    }

    @Override
    public void reload() {
        reload(getConfiguration());
    }

    @Override
    public void reload(final ResourceConfig configuration) {
        handler.onShutdown(this);

        handler = new ApplicationHandler(configuration);
        handler.onReload(this);
        handler.onStartup(this);
    }
}

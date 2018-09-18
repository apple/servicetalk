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
package io.servicetalk.examples.http.helloworld.async;

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.netty.HttpClients;

public final class HelloWorldClient {
    public static void main(String[] args) {
        try (HttpClient client = HttpClients.forSingleAddress("localhost", 8080).build()) {
            client.request(client.get("http://localhost:8080/sayHello"))
                    .subscribe(System.out::println);
        }
    }
}

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
package io.servicetalk.opentracing.http;

import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.opentracing.Scope;
import org.reactivestreams.Subscription;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static io.opentracing.tag.Tags.ERROR;
import static io.opentracing.tag.Tags.HTTP_STATUS;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;

final class TracingUtils {
    private TracingUtils() {
        // no instances
    }

    static void tagErrorAndClose(Scope currentScope) {
        ERROR.set(currentScope.span(), true);
        currentScope.close();
    }

    static boolean isError(HttpResponseMetaData metaData) {
        return metaData.status().statusClass().equals(SERVER_ERROR_5XX);
    }

    static UnaryOperator<StreamingHttpResponse> tracingMapper(Scope scope,
                                                              Predicate<HttpResponseMetaData> errorChecker) {
        return resp -> resp.transformRawPayloadBody(pub ->
                pub.doAfterSubscriber(() -> new org.reactivestreams.Subscriber<Object>() {
                    @Override
                    public void onSubscribe(final Subscription s) {
                    }

                    @Override
                    public void onNext(final Object o) {
                    }

                    @Override
                    public void onError(final Throwable t) {
                        tagErrorAndClose(scope);
                    }

                    @Override
                    public void onComplete() {
                        HTTP_STATUS.set(scope.span(), resp.status().code());
                        try {
                            if (errorChecker.test(resp)) {
                                ERROR.set(scope.span(), true);
                            }
                        } finally {
                            scope.close();
                        }
                    }
                }).doOnCancel(() -> tagErrorAndClose(scope)));
    }

    static StreamingHttpResponse tracingMapper(StreamingHttpResponse resp,
                                               Scope scope, Predicate<HttpResponseMetaData> errorChecker) {
        return tracingMapper(scope, errorChecker).apply(resp);
    }
}

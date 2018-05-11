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
package io.servicetalk.rxjava.context;

import io.servicetalk.concurrent.context.AsyncContext;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Function;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * Hooks to ensure {@link AsyncContext} is propagated into RxJava operators. Hooks can be installed all at once
 * using {@link #install()}, or if you need to compose hooks with your own hooks (e.g. install hooks before/after
 * one another) you can use the {@code HOOK} constants directly. Use {@link RxJavaPlugins#reset()} to remove hooks.
 */
public final class RxJavaContextHooks {
    public static final Function<Completable, Completable> COMPLETABLE_HOOK = CompletableContextWrapper::new;

    public static final Function<Single, Single> SINGLE_HOOK = SingleContextWrapper::new;

    public static final Function<Maybe, Maybe> MAYBE_HOOK = MaybeContextWrapper::new;

    public static final Function<Observable, Observable> OBSERVABLE_HOOK = ObservableContextWrapper::new;

    public static final Function<ConnectableObservable, ConnectableObservable> CONNECTABLE_OBSERVABLE_HOOK = ConnectableObservableContextWrapper::new;

    public static final Function<Flowable, Flowable> FLOWABLE_HOOK = FlowableContextWrapper::new;

    public static final Function<ConnectableFlowable, ConnectableFlowable> CONNECTABLE_FLOWABLE_HOOK = ConnectableFlowableContextWrapper::new;

    private RxJavaContextHooks() {
        // no instances
    }

    /**
     * Convenience method to install all {@link AsyncContext}-propagation hooks.
     */
    public static void install() {
        Function<? super Completable, ? extends Completable> onCompletable = RxJavaPlugins.getOnCompletableAssembly();
        if (onCompletable == null) {
            RxJavaPlugins.setOnCompletableAssembly(COMPLETABLE_HOOK);
        } else {
            RxJavaPlugins.setOnCompletableAssembly(completable -> COMPLETABLE_HOOK.apply(onCompletable.apply(completable)));
        }

        Function<? super Single, ? extends Single> onSingle = RxJavaPlugins.getOnSingleAssembly();
        if (onSingle == null) {
            RxJavaPlugins.setOnSingleAssembly(SINGLE_HOOK);
        } else {
            RxJavaPlugins.setOnSingleAssembly(single -> SINGLE_HOOK.apply(onSingle.apply(single)));
        }

        Function<? super Maybe, ? extends Maybe> onMaybe = RxJavaPlugins.getOnMaybeAssembly();
        if (onMaybe == null) {
            RxJavaPlugins.setOnMaybeAssembly(MAYBE_HOOK);
        } else {
            RxJavaPlugins.setOnMaybeAssembly(maybe -> MAYBE_HOOK.apply(onMaybe.apply(maybe)));
        }

        Function<? super Observable, ? extends Observable> onObservable = RxJavaPlugins.getOnObservableAssembly();
        if (onObservable == null) {
            RxJavaPlugins.setOnObservableAssembly(OBSERVABLE_HOOK);
        } else {
            RxJavaPlugins.setOnObservableAssembly(observable -> OBSERVABLE_HOOK.apply(onObservable.apply(observable)));
        }

        Function<? super ConnectableObservable, ? extends ConnectableObservable> onConnectable = RxJavaPlugins.getOnConnectableObservableAssembly();
        if (onConnectable == null) {
            RxJavaPlugins.setOnConnectableObservableAssembly(CONNECTABLE_OBSERVABLE_HOOK);
        } else {
            RxJavaPlugins.setOnConnectableObservableAssembly(connectable -> CONNECTABLE_OBSERVABLE_HOOK.apply(onConnectable.apply(connectable)));
        }

        Function<? super Flowable, ? extends Flowable> onFlowable = RxJavaPlugins.getOnFlowableAssembly();
        if (onFlowable == null) {
            RxJavaPlugins.setOnFlowableAssembly(FLOWABLE_HOOK);
        } else {
            RxJavaPlugins.setOnFlowableAssembly(flowable -> FLOWABLE_HOOK.apply(onFlowable.apply(flowable)));
        }

        Function<? super ConnectableFlowable, ? extends ConnectableFlowable> onConnectableFlowable = RxJavaPlugins.getOnConnectableFlowableAssembly();
        if (onConnectableFlowable == null) {
            RxJavaPlugins.setOnConnectableFlowableAssembly(CONNECTABLE_FLOWABLE_HOOK);
        } else {
            RxJavaPlugins.setOnConnectableFlowableAssembly(connectableFlowable -> CONNECTABLE_FLOWABLE_HOOK.apply(onConnectableFlowable.apply(connectableFlowable)));
        }
    }
}

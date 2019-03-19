# ServiceTalk

ServiceTalk is a cross-platform network application framework with APIs tailored to specific protocols (e.g. HTTP/1.x,
HTTP/2.x, etc...) and supports multiple [programming paradigms](#programming-paradigms).

It is built on [Netty](https://netty.io) and is designed to provide most of the performance/scalability benefits of
Netty for common networking protocols used in service to service communication. ServiceTalk provides "smart client" like
features such as client side load balancing and service discovery integration.

## Conceptual Overview

ServiceTalk is intended to provide a common and extensible networking abstraction on top of a lower-level networking
framework (e.g. [Netty](https://netty.io)). Netty is a great low-level networking framework, but from the perspective of
application developers who need service to service communication it presents a few challenges:

- Threading Model
  - fully asynchronous and requires knowledge of EventLoop threading model
  - back-pressure requires manual association of source of data and sink of data
  - executing CPU intensive or "blocking" code requires manual thread hops
  - ordering issues when code executes on the EventLoop and off the EventLoop thread
- Usability
  - APIs not targeted toward specific protocols
  - Asynchronous programming paradigm presents a barrier to entry when it isn't currently required for scalability
  - Error propagation follows multiple paths depending on the event and state of Channel
- Lacking Feature Set
  - Smart Client (e.g. client side load balancing, service discovery, retry) features missing

ServiceTalk addresses these challenges and provides a framework that supports multiple
[programming paradigms](#programming-paradigms). ServiceTalk accomplishes this by building on a fully asynchronous
non-blocking I/O core and taking care of the threading model complexities internally.

### Programming paradigms

When developing a new application it may not be clear if the complexity of asynchronous control flow is justified. It
maybe the case that initially the scale is relatively small, but over time the scale may grow. The scaling or response
size characteristics may not be uniform for all APIs offered by the application (e.g. health check vs file server).
ServiceTalk is designed to evolve with your application so that you can get started quickly and avoid/defer the
complexity of asynchronous control flow in these cases. This can dramatically lower the bar to entry for ServiceTalk
compared with most non-blocking I/O frameworks and avoid "application re-write" if scaling/data size characteristics
change over time. 

#### Blocking vs Synchronous

ServiceTalk APIs may use the term "blocking" in areas where the APIs may be identified as "synchronous". "blocking" in
this context is meant to declare that the API "may block" the calling thread. This is done because there is no general
way to determine if a method will return synchronous or block the calling thread, and "blocking" is the least common
denominator.

#### [Blocking](#blocking-vs-synchronous) and Aggregated

This API paradigm is similar to concepts from `java.io` and generally blocks the calling thread until all I/O is
completed. The result is aggregated into a single object (e.g.
[Files.readAllLines](https://docs.oracle.com/javase/8/docs/api/java/nio/file/Files.html#readAllLines-java.nio.file.Path-)).

#### [Blocking](#blocking-vs-synchronous) and Streaming

This API paradigm is similar to concepts from `java.io` and generally blocks the calling thread until I/O is
flushed/received. The result can be provided/processed in a streaming fashion (e.g.
[InputStream](https://docs.oracle.com/javase/8/docs/api/java/io/InputStream.html) or
[OutputStream](https://docs.oracle.com/javase/8/docs/api/java/io/OutputStream.html)) however processing each chunk of
the stream may also block the calling thread.

#### Asynchronous and Aggregated

This API paradigm performs I/O asynchronously (e.g. the calling thread is not blocked) and the user is notified when all
the I/O is complete. ServiceTalk provides [Reactive Streams](http://www.reactive-streams.org) compatible
[Asynchronous Primitives](#asynchronous-primitives) but using this API is similar to using a `Future`/`Promise` based
API (e.g. 
[CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html) and
[CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)).

#### Asynchronous and Streaming

This API paradigm performs I/O asynchronously (e.g. the calling thread is not blocked) and the user can provide/process
the I/O in chunks (as opposed to in a single `Object`). ServiceTalk provides
[Reactive Streams](http://www.reactive-streams.org) compatible [Asynchronous Primitives](#asynchronous-primitives) to
enable this API paradigm.

## Supported JVM

The minimum supported JDK version is 1.8.

## Compatibility

ServiceTalk follows [SemVer 2.0.0](https://semver.org/#semantic-versioning-200). API/ABI breaking changes will require
package renaming for that module to avoid runtime class path conflicts. Note that `0.x.y` releases are not stable and
are permitted to break API/ABI.

## Basic Architecture

### Asynchronous Primitives

ServiceTalk provides [Reactive Streams](http://www.reactive-streams.org) compatible
[Asynchronous Primitives](#asynchronous-primitives).

Note that all asynchronous primitives are "lazy"/"cold" in that the action
they represent does not start until someone is "listening" for the results. This is different from "eager"/"hot"
[CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) usages in
that the work being done to complete the
[CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) is already
happening regardless if anyone is "listening" for the results. 

#### [Publisher](servicetalk-concurrent-api/src/main/java/io/servicetalk/concurrent/api/Publisher.java)

A [Publisher](servicetalk-concurrent-api/src/main/java/io/servicetalk/concurrent/api/Publisher.java) represents an
asynchronous stream of data. There is a
[`PublisherSource.Subscriber<T>`](servicetalk-concurrent/src/main/java/io/servicetalk/concurrent/PublisherSource.java#L59-L102)
that [subscribes](servicetalk-concurrent/src/main/java/io/servicetalk/concurrent/PublisherSource.java#L43) and requests
more data via a
[PublisherSource.Subscription](servicetalk-concurrent/src/main/java/io/servicetalk/concurrent/PublisherSource.java#L116-L129).

Users are generally not expected to `subscribe` to a `PublisherSource`, or even deal directly with a `PublisherSource`.
Instead most users are expected to use the
[Publisher](servicetalk-concurrent-api/src/main/java/io/servicetalk/concurrent/api/Publisher.java) API which provides
many operators to define asynchronous and streaming control flow.

For more details on the API contract please review the
[Reactive Streams Specification](https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.2/README.md#specification).

#### [Single](servicetalk-concurrent-api/src/main/java/io/servicetalk/concurrent/api/Single.java)

A [Single](servicetalk-concurrent-api/src/main/java/io/servicetalk/concurrent/api/Single.java) shares all the same
semantics as [Publisher](#publisher), but it either terminates with a
[single value, or an error](servicetalk-concurrent/src/main/java/io/servicetalk/concurrent/SingleSource.java#L45-L71).

Users are generally not expected to `subscribe` to a `SingleSource`, or even deal directly with a `SingleSource`.
Instead most users are expected to use the
[Single](servicetalk-concurrent-api/src/main/java/io/servicetalk/concurrent/api/Single.java) API which provides many
operators to define asynchronous and streaming control flow.

#### [Completable](servicetalk-concurrent-api/src/main/java/io/servicetalk/concurrent/api/Completable.java)

A [Single](servicetalk-concurrent-api/src/main/java/io/servicetalk/concurrent/api/Single.java) shares all the same
semantics as [Publisher](#publisher), but it either terminates
[successfully, or with an error](servicetalk-concurrent/src/main/java/io/servicetalk/concurrent/CompletableSource.java#L39-63).

Users are generally not expected to `subscribe` to a `CompletableSource`, or even deal directly with a
`CompletableSource`. Instead most users are expected to use the
[Completable](servicetalk-concurrent-api/src/main/java/io/servicetalk/concurrent/api/Completable.java) API which
provides many operators to define asynchronous and streaming control flow.

### Design Philosophy

ServiceTalk is designed to designed to provide an extensible core and tailored APIs to networking protocols. ServiceTalk
does not intend to provide abstractions for low level networking primitives (e.g. Channels, EventLoop, TLS, etc...) but
instead uses these primitives to provide an higher level API in numerous
[programming paradigms](#programming-paradigms).

The project is divided into many modules to decouple the user facing API from implementation details. This gives users
freedom to choose only the functionality they need, and also allows us to evolve each module independently. Note that
these modules may be divided out into independent repositories to decouple from the core and enable independent
versioning.  
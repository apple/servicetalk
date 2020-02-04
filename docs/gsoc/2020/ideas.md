# Google Summer of Code 2020 Ideas

1. [Add compression and decompression support to ServiceTalk](#add-compression-and-decompression-support-to-servicetalk)
    * [Expected outcomes](#expected-outcomes)
    * [Required skills](#required-skills)
    * [Complexity](#complexity)
    * [Mentor](#mentor)
2. [Add UDP support to ServiceTalk](#add-udp-support-to-servicetalk)
    * [Expected outcomes](#expected-outcomes-1)
    * [Required skills](#required-skills-1)
    * [Complexity](#complexity-1)
    * [Mentor](#mentor-1)    
3. [Add new asynchronous operators to ServiceTalk](#add-new-asynchronous-operators-to-servicetalk)
    * [Expected outcomes](#expected-outcomes-2)
    * [Required skills](#required-skills-2)
    * [Complexity](#complexity-2)
    * [Mentor](#mentor-2)    
    
## Add compression and decompression support to ServiceTalk

A core design philosophy of ServiceTalk is to provide [symmetric APIs](https://docs.servicetalk.io/#CrossProtocolApiSymmetry) across multiple different application protocols. This API symmetry enables users of ServiceTalk to extend their understanding of one protocol to another in quick time. This also enables ServiceTalk to reuse [common constructs](https://servicetalk.io/tree/master/servicetalk-transport-netty) across these protocols hence accelerating the process of writing new protocol implementations. Additionally ServiceTalk uses [netty](http://netty.io/), to implement the transport layer, delegating the protocol processing to netty and adding ServiceTalk specific concepts (like ReactiveStreams based flowcontrol, asynchronous primitives, etc) on top.

One of the common feature across different protocols is the ability to compress and decompress application level messages (eg: HTTP payloads). Compression optimizes for reducing data transfer over the network but requires more complex computations making it a CPU vs network bandwidth trade off. For some use cases like data transfers over WAN, reducing network bandwidth usage may be useful. Further, using different compression algorithms give users flexibility to balance between compression ratio and CPU usage.

ServiceTalk would like to provide compression/decompression as an optional feature for users while following the core design philosophy of cross-protocol API symmetry. Doing this task will include the following parts:

* Provide a protocol-neutral compression/decompression API using [ServiceTalk buffers](https://servicetalk.io/tree/master/servicetalk-buffer-api).
* Provide implementations for some compression algorithms, preferrably reusing the implementations in netty.
* Integrate the common compression/decompression API with all supported protocols in ServiceTalk.


### Expected outcomes

* A protocol-neutral compression/decompression API using ServiceTalk buffers.
* Implementations of the above API for selective compression/decompression algorithms as decided by the student and the mentor.
* Integrate with HTTP client and server to implement [HTTP compression scheme](https://en.wikipedia.org/wiki/HTTP_compression#Compression_scheme_negotiation).
* Integrate with gRPC client and server to implement [gRPC compression scheme](https://github.com/grpc/grpc/blob/master/doc/compression.md).
* Test coverage, documentation, and examples for new functionality.

### Required skills

* Experience with concurrent programming in Java.
* Experience with asynchronous programming in Java.
* Familiarity with HTTP and gRPC protocols.
* Familiarity with compression/decompression concept.
* Familiarity with test frameworks (jUnit, hamcrest, mockito).

### Complexity
 
Medium.

### Mentor

https://github.com/NiteshKant

## Add UDP support to ServiceTalk

A core design philosophy of ServiceTalk is to provide [symmetric APIs]() across multiple different application protocols. This API symmetry enables users of ServiceTalk to leverage their understanding of one protocol and apply it to a new protocol. It also enables ServiceTalk to reuse [common constructs](https://servicetalk.io/tree/master/servicetalk-transport-netty) across these protocols hence accelerating the process of writing new protocol implementations. Additionally ServiceTalk uses [netty](http://netty.io/), to implement the transport layer, delegating the protocol processing to netty and adding ServiceTalk specific concepts (like ReactiveStreams based flowcontrol, asynchronous primitives, etc) on top. Due to this reuse, implementing a new protocol in ServiceTalk is less complex relative to implementing a new protocol from scratch.

ServiceTalk’s approach to prioritizing new protocol implementations is purely based on the need expressed by its users. We are also a part of the netty community and prefer to add the protocol to netty first and then use it inside ServiceTalk.. Currently, ServiceTalk implements [HTTP (both HTTP/1 and HTTP/2)](https://apple.github.io/servicetalk/servicetalk-http-api/SNAPSHOT/index.html) and [gRPC](https://apple.github.io/servicetalk/servicetalk-grpc-api/SNAPSHOT/index.html) protocols. [User Datagram Protocol (UDP)](https://tools.ietf.org/html/rfc768) is a protocol which has been requested by the community but is not yet supported by ServiceTalk. UDP is specially interesting to facilitate low latency, small data payload usecases that do not require reliable delivery. UDP is somewhat unique when compared to other currently supported protocols in ServiceTalk due to the following reasons:

* _Connection-less_: UDP is a connection less protocol unlike HTTP and gRPC.
* _Lack of application level information_: UDP is an [OSI layer 4](https://en.wikipedia.org/wiki/Transport_layer) protocol and hence does not define a request (or response) structure unlike HTTP and gRPC.
* _Lack of request-response correlation_: UDP does not define any semantics of how to implement request-response interactions unlike HTTP and gRPC.

These unique characteristics will require the implementors of UDP in ServiceTalk to spend time in designing user facing APIs and some of the common transport constructs in ServiceTalk may have to be either refactored or written from scratch. 

### Expected outcomes

* UDP implementation for ServiceTalk.
* Test coverage, documentation, and examples for UDP.
* Blocking streaming and asynchronous streaming [programming paradigms](https://docs.servicetalk.io/programming-paradigms.html) support for UDP.

### Required skills

* Experience with concurrent programming in Java.
* Experience with asynchronous programming in Java.
* Familiarity with UDP protocol.
* Familiarity with test frameworks (jUnit, hamcrest, mockito).

### Complexity

Hard.

### Mentor

https://github.com/NiteshKant


## Add new asynchronous operators to ServiceTalk

Users of ServiceTalk can choose from different [programming models](https://apple.github.io/servicetalk/servicetalk/SNAPSHOT/programming-paradigms.html) while using clients and servers provided by ServiceTalk. These different programming models are built on top of ServiceTalk internals that always use the [asynchronous primitives](https://apple.github.io/servicetalk/servicetalk-concurrent-api/SNAPSHOT/asynchronous-primitives.html) and the various [operators](https://apple.github.io/servicetalk/servicetalk-concurrent-api/SNAPSHOT/asynchronous-primitives.html#operators) that these sources provide. The concept of operators in not unique to ServiceTalk and for some operators there is prior art in existing asynchronous library. The potential list of operators that can be defined for an asynchronous source is pretty long and the applicability may vary between different usecases. ServiceTalk’s approach to adding new operators has been on-demand either to address the request of our users or because they were required to implement ServiceTalk’s internals. This approach of prioritization has served well and in the process there are a good number of supported operators. However, these supported operators are not comprehensive and may not be sufficient for certain use cases. 

We would like to add new operators to ServiceTalk specifically focussing on stream processing. *Few examples* of these operators are:

* Publisher#flatMapMerge() :  When every item in an asynchronous stream (Publisher) produces another asynchronous stream (Publisher) and the *resulting items* from all streams can be emitted in *any order*. This operator is typically used while implementing streaming services.
* Publisher#flatMapConcatSingle() :  When every item in an asynchronous stream (Publisher) asynchronously produces a single item (Single) and the *resulting items* from should be emitted in the* order the original item was received*. This operator can be used to implement request processing for an HTTP server.
* Publisher#window(): When items in an asynchronous stream (Publisher) can be batched within a window of time or specific size. This operator can be used to create arbitrary flush boundaries while writing data or to de-duplicate items in an infinite stream.

### Expected outcomes

* Add the operators to ServiceTalk that were mutually agreed upon by the student and mentor.
* Unit tests for these operators.
* ReactiveStreams TCK tests.
* JMH benchmarks to demonstrate if a particular approach was chosen for performance reasons.

### Required skills

* Lock free and wait free concurrent programming in Java.
* Understanding of [ReactiveStreams specification](https://github.com/reactive-streams/reactive-streams-jvm#specification).
* Familiarity with test frameworks.

### Complexity
 
Hard.

### Mentor

https://github.com/NiteshKant

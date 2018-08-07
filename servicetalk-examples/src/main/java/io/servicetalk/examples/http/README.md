## HTTP Examples

This package contains all examples for HTTP protocol. 

### Hello World

An obligatory "Hello World" example for HTTP.

#### Blocking 

This is the example using HTTP blocking APIs.

- [Server](helloworld/blocking/streaming/HelloWorldBlockingServer.java) An hello world server that responds with "Hello World!" for every request.
- [Service](helloworld/blocking/streaming/HelloWorldBlockingService.java) A `BlockingHttpService` used in the [Server](helloworld/blocking/streaming/HelloWorldBlockingServer.java) that processes all requests received by this server.
- [Client](helloworld/blocking/streaming/HelloWorldBlockingClient.java) A client that sends a request to the [Server](helloworld/blocking/streaming/HelloWorldBlockingServer.java).

#### Asynchronous 

This is the example using HTTP asynchronous APIs.

- [Server](helloworld/async/streaming/HelloWorldServer.java) An hello world server that responds with "Hello World!" for every request.
- [Service](helloworld/async/streaming/HelloWorldService.java) An `HttpService` used in the [Server](helloworld/async/streaming/HelloWorldServer.java) that processes all requests received by this server.
- [Client](helloworld/async/streaming/HelloWorldClient.java) A client that sends a request to the [Server](helloworld/async/streaming/HelloWorldServer.java).

### Aggregated payload

This example demonstrates asynchronous request processing where the payload body is aggregated into a single object instead of a stream.

- [Server](helloworld/async/aggregated/AggregatingPayloadServer.java) A server that echoes the request from the client.
- [Service](helloworld/async/aggregated/RequestAggregationService.java) A `AggregatedHttpService` used in the [Server](helloworld/async/aggregated/AggregatingPayloadServer.java) that processes all requests received by this server.
- [Client](helloworld/async/aggregated/AggregatingPayloadClient.java) A client that sends a large request to the [Server](helloworld/async/aggregated/AggregatingPayloadServer.java) and receives the echoed response as a single content.

### Streaming

ServiceTalk supports streaming payload with both blocking and asynchronous HTTP APIs. 
This example demonstrates how to use these APIs to do streaming payload consumption.

#### Blocking 

Streaming payload consumption with blocking APIs. The APIs will block if content is requested but there is no content available.

- [Server](streaming/StreamingBlockingServer.java) A server that demonstrates blocking APIs with streaming payload.
- [Service](streaming/StreamingBlockingService.java) A `BlockingHttpService` used in the [Server](streaming/StreamingBlockingServer.java) that processes all requests received by this server.
- [Client](streaming/StreamingBlockingClient.java) A client that sends a request to the [Server](streaming/StreamingBlockingServer.java) and iterates over the response payload.

#### Asynchronous 

Streaming payload consumption with asynchronous APIs.

- [Server](streaming/StreamingServer.java) A server that demonstrates asynchronous APIs with streaming payload.
- [Service](streaming/StreamingService.java) An `HttpService` used in the [Server](streaming/StreamingServer.java) that processes all requests received by this server.
- [Client](streaming/StreamingBlockingClient.java) A client that sends a request to the [Server](streaming/StreamingServer.java) and iterates the payload asynchronously.


### JAX-RS

ServiceTalk provides a JAX-RS implementation that can plugin to ServiceTalk APIs. 
This example demonstrates how to use these APIs.

#### Hello world 

A simple "Hello World" example built using JAX-RS.

- [Server](helloworld/jaxrs/HelloWorldJaxRsServer.java) A JAX-RS based hello world server that demonstrates how to write blocking as well as asynchronous resource methods. 
- [Resource](helloworld/jaxrs/HelloWorldJaxRsResource.java) A JAX-RS resource having different methods for blocking and asynchronous interactions.

This example does not have a client yet but one can use curl to send requests like:

`curl http://localhost:8080/greetings/hello`

More examples of how to use the resource can be found in the [Resource](helloworld/jaxrs/HelloWorldJaxRsResource.java) javadocs.

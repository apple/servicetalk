## HTTP Examples

This package contains all examples for HTTP protocol. 

### Hello World

An obligatory "Hello World" example for HTTP.

#### Blocking 

This is the example using HTTP blocking APIs.

- [Server](helloworld/HelloWorldBlockingServer.java) An hello world server that responds with "Hello World!" for every request.
- [Service](helloworld/HelloWorldBlockingService.java) A `BlockingHttpService` used in the [Server](helloworld/HelloWorldBlockingServer.java) that processes all requests received by this server.
- [Client](helloworld/HelloWorldBlockingClient.java) A client that sends a request to the [Server](helloworld/HelloWorldBlockingServer.java).

#### Asynchronous 

This is the example using HTTP asynchronous APIs.

- [Server](helloworld/HelloWorldServer.java) An hello world server that responds with "Hello World!" for every request.
- [Service](helloworld/HelloWorldService.java) An `HttpService` used in the [Server](helloworld/HelloWorldServer.java) that processes all requests received by this server.
- [Client](helloworld/HelloWorldClient.java) A client that sends a request to the [Server](helloworld/HelloWorldServer.java).

### Aggregated payload

This example demonstrates asynchronous request processing where the payload body is aggregated into a single object instead of a stream.

- [Server](aggregation/AggregatingPayloadServer.java) A server that echoes the request from the client.
- [Service](aggregation/RequestAggregationService.java) A `AggregatedHttpService` used in the [Server](aggregation/AggregatingPayloadServer.java) that processes all requests received by this server.
- [Client](aggregation/AggregatingPayloadClient.java) A client that sends a large request to the [Server](aggregation/AggregatingPayloadServer.java) and receives the echoed response as a single content.

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

- [Server](jaxrs/helloworld/HelloWorldJaxRsServer.java) A JAX-RS based hello world server that demonstrates how to write blocking as well as asynchronous resource methods. 
- [Resource](jaxrs/helloworld/HelloWorldJaxRsResource.java) A JAX-RS resource having different methods for blocking and asynchronous interactions.

This example does not have a client yet but one can use curl to send requests like:

`curl http://localhost:8080/greetings/hello`

More examples of how to use the resource can be found in the [Resource](jaxrs/helloworld/HelloWorldJaxRsResource.java) javadocs.

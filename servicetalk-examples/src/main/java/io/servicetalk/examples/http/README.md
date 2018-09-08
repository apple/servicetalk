## HTTP Examples

This package contains all examples for HTTP protocol. 

### Hello World

An obligatory "Hello World" example for HTTP.

### Asynchronous + Aggregated

This example demonstrates asynchronous request processing where the payload body is aggregated into a single object
instead of a stream.

- [Server](helloworld/async/aggregated/HelloWorldClient.java) A server that echoes the request from the client.
- [Service](helloworld/async/aggregated/HelloWorldService.java) A `HttpService` used in the
[Server](helloworld/async/aggregated/HelloWorldServer.java) that processes all requests received by this server.
- [Client](helloworld/async/aggregated/HelloWorldClient.java) A client that sends a large request to the
[Server](helloworld/async/aggregated/HelloWorldServer.java) and receives the echoed response as a single content.

#### Asynchronous + Streaming

This example demonstrates asynchronous request processing where the payload body is a stream.

- [Server](helloworld/async/streaming/HelloWorldStreamingServer.java) An hello world server that responds with
"Hello World!" for every request.
- [Service](helloworld/async/streaming/HelloWorldStreamingService.java) An `StreamingHttpService` used in the
[Server](helloworld/async/streaming/HelloWorldStreamingServer.java) that processes all requests received by this server.
- [Client](helloworld/async/streaming/HelloWorldStreamingClient.java) A client that sends a request to the
[Server](helloworld/async/streaming/HelloWorldStreamingServer.java).

#### Blocking + Aggregated

This example demonstrates blocking request processing where the payload body is aggregated into a single object. The
APIs will block if content is requested but there is no content available.

- [Server](streaming/StreamingBlockingServer.java) A server that demonstrates blocking APIs with streaming payload.
- [Service](streaming/StreamingBlockingService.java) A `BlockingHttpService` used in the
[Server](streaming/StreamingBlockingServer.java) that processes all requests received by this server.
- [Client](streaming/StreamingBlockingClient.java) A client that sends a request to the
[Server](streaming/StreamingBlockingServer.java) and iterates over the response payload.

#### Blocking + Streaming 

This is the example using HTTP blocking APIs.

- [Server](helloworld/blocking/streaming/HelloWorldBlockingServer.java) An hello world server that responds with
"Hello World!" for every request.
- [Service](helloworld/blocking/streaming/HelloWorldBlockingService.java) A `BlockingStreamingHttpService` used in the
[Server](helloworld/blocking/streaming/HelloWorldBlockingServer.java) that processes all requests received by this
server.
- [Client](helloworld/blocking/streaming/HelloWorldBlockingClient.java) A client that sends a request to the
[Server](helloworld/blocking/streaming/HelloWorldBlockingServer.java).

### JAX-RS

ServiceTalk provides a JAX-RS implementation that can plugin to ServiceTalk APIs.
This example demonstrates how to use these APIs, and how different API variations (e.g. asynchronous/blocking and
aggregated/streaming) are exposed. 

#### Hello world 

A simple "Hello World" example built using JAX-RS.

- [Server](helloworld/jaxrs/HelloWorldJaxRsServer.java) A JAX-RS based hello world server that demonstrates how to write
blocking as well as asynchronous resource methods. 
- [Resource](helloworld/jaxrs/HelloWorldJaxRsResource.java) A JAX-RS resource having different methods for blocking and
asynchronous interactions.

This example does not have a client yet but one can use curl to send requests like:

`curl http://localhost:8080/greetings/hello`

More examples of how to use the resource can be found in the [Resource](helloworld/jaxrs/HelloWorldJaxRsResource.java)
javadocs.

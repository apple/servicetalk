## HTTP Examples

This package contains examples for [HTTP](https://tools.ietf.org/html/rfc7231) protocol.



### Hello World

An obligatory "Hello World" example for HTTP.


#### Asynchronous + Aggregated

This example demonstrates asynchronous request processing where the payload body is aggregated into a single object
instead of a stream.

- [HelloWorldServer](helloworld/async/HelloWorldClient.java) - a server that demonstrates asynchronous API and responds
with a simple `Hello World!` response body as a `text/plain`.
- [HelloWorldClient](helloworld/async/HelloWorldClient.java) - a client that sends a `GET` request to the configured
single address of the [server](helloworld/async/HelloWorldServer.java) and receives the response as a single content.
- [HelloWorldUrlClient](helloworld/async/HelloWorldUrlClient.java) - a client that sends a `GET` request to the
specified URL in absolute-form and receives the response as a single content.


#### Asynchronous + Streaming

This example demonstrates asynchronous request processing where the payload body is a stream.

- [HelloWorldStreamingServer](helloworld/async/streaming/HelloWorldStreamingServer.java) - a server that responds with a
stream of `text/plain` payload body for every request.
- [HelloWorldStreamingClient](helloworld/async/streaming/HelloWorldStreamingClient.java) - a client that sends a `GET`
request to the configured single address of the [server](helloworld/async/streaming/HelloWorldStreamingServer.java) and
receives the response payload body as a stream of buffers.
- [HelloWorldStreamingUrlClient](helloworld/async/streaming/HelloWorldStreamingUrlClient.java) - a client that sends a
`GET` request to the specified URL in absolute-form and receives the response payload body as a stream of buffers.


#### Blocking + Aggregated

This example demonstrates blocking request processing where the payload body is aggregated into a single object. The
APIs will block if content is requested but there is no content available.

- [BlockingHelloWorldServer](helloworld/blocking/BlockingHelloWorldServer.java) - a server that demonstrates blocking
API and responds with a simple `Hello World!` response body as a `text/plain`.
- [BlockingHelloWorldClient](helloworld/blocking/BlockingHelloWorldClient.java) - a client that sends a `GET` request to
the configured single address of the [server](helloworld/blocking/BlockingHelloWorldServer.java) and receives the
response payload body as one aggregated object.
- [BlockingHelloWorldUrlClient](helloworld/blocking/BlockingHelloWorldUrlClient.java) - a client that sends a `GET`
request to the specified URL in absolute-form and receives the response payload body as one aggregated object.


#### Blocking + Streaming

This example demonstrates blocking request processing where the payload body is a blocking iterable stream.

- [BlockingHelloWorldStreamingServer](helloworld/blocking/streaming/BlockingHelloWorldStreamingServer.java) - a server
that responds with an iterable stream of `text/plain` payload body for every request.
- [BlockingHelloWorldStreamingClient](helloworld/blocking/streaming/BlockingHelloWorldStreamingClient.java) - a client
that sends a `GET` request to the configured single address of the
[server](helloworld/blocking/streaming/BlockingHelloWorldStreamingServer.java) and receives the response payload body as
a blocking iterable stream of buffers.
- [BlockingHelloWorldStreamingUrlClient](helloworld/blocking/streaming/BlockingHelloWorldStreamingUrlClient.java) - a
client that sends a `GET` request to the specified URL in absolute-form and receives the response payload body as a
blocking iterable stream of buffers.



### Serialization

A similar to "Hello World" examples, which demonstrate [asynchronous-aggregated](serialization/async),
[asynchronous-streaming](serialization/async/streaming), [blocking-aggregated](serialization/blocking), and
[blocking-streaming](serialization/blocking/streaming) client and server with JSON serialization of simple pojo classes.

Client sends a `GET` request with JSON payload [PojoRequest](serialization/PojoRequest.java) and expects a response with
`Content-Type: application/json` and [MyPojo](serialization/MyPojo.java) as a payload.



### JAX-RS

ServiceTalk provides a JAX-RS implementation that can plugin to ServiceTalk APIs.
This example demonstrates how to use these APIs, and how different API variations (e.g. asynchronous/blocking and
aggregated/streaming) are exposed.


#### Hello world

A simple "Hello World" example built using JAX-RS.

- [HelloWorldJaxRsServer](jaxrs/HelloWorldJaxRsServer.java) - a JAX-RS based hello world server that demonstrates how to
write blocking as well as asynchronous resource methods.
- [HelloWorldJaxRsResource](jaxrs/HelloWorldJaxRsResource.java) - a JAX-RS resource having different methods for
blocking and asynchronous interactions.

This example does not have a client yet but one can use curl to send requests like:

```
curl http://localhost:8080/greetings/hello
```

More examples of how to use the resource can be found in the
[HelloWorldJaxRsResource](jaxrs/HelloWorldJaxRsResource.java) javadocs.



### Service Composition

An advanced example which demonstrates a composition of various ServiceTalks services in one application.
For more information see [service.composition](service/composition) package.

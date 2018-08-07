# Service Composition

__This is an advanced example and not necessarily intended to be a "getting started" example.__

This example is meant to resemble a somewhat realistic application from a control flow and serialization perspective
while demonstrating how ServiceTalk's API variations enable users to choose their preferred programming model.

Note this example also contains "backend" services which simulate databases and keep the example self contained. 
These backend services do show more usage of ServiceTalk APIs but are not the focus of the example.

## How to run the example?

This example has two distinct bootstrap locations:

- [GatewayServer](GatewayServer.java) is a main class and starts only the gateway server with all the endpoints. This
server is started on port `8085`.
- [BackendsStarter](backends/BackendsStarter.java) is a main class that starts the other services, viz.,
`Recommendation`, `Metadata`, `User` and `Ratings` service.
 
# Usecase

In order to demonstrate a complex service composition usecase, we take the following abstract example:

## Gateway Service

A service that composes multiple downstream service results into an externally consumable result. 

### Result

The result is a complex object [FullRecommendation](pojo/FullRecommendation.java) as follows:

```java
public final class FullRecommendation {
    private Metadata entity;
    private User recommendedBy;
    private Rating overallRating;
    // Getters and setters removed for brevity
}
```

Each component of this data is fetched from different backends as described below. 

## Recommendation Backend

A [backend service](backends/RecommendationBackend.java) that provides recommendation (entities) for a particular user.
Each recommended entity contains:

- Recommended entity ID.
- User ID who recommended this entity to this user.

This backend provides a two endpoints:

1. Streaming: An push based endpoint which keeps pushing new recommendations when available. 
To simulate real life scenarios, we push a recommendation periodically.
2. Aggregated: An aggregated endpoint that sends all currently available recommendations as a list of entities. 

Since a recommendation returned by this backend is not externally consumable (only contains IDs), this incomplete
information is materialized using the following backends:

## Metadata Backend

A [backend service](backends/MetadataBackend.java) that provides details about the entity given an entityId.

## User Backend

A [backend service](backends/UserBackend.java) that provides details about a user given an userId.

## Rating Backend

A [backend service](backends/RatingBackend.java) that provides ratings of an entity given an entityId.

## Objective

The objective is to demonstrate what will be the different ways in which these service calls can be composed to provide
the final materialized result.

## Gateway Endpoints

In this example we provide three different endpoints on the gateway server to also demonstrate how different programming 
paradigms can co-exist in a single HTTP server. 
 
### Asynchronous streaming

This is an [endpoint](GatewayService.java) that streams `FullRecommendation` JSON. 
This is a simulation of a push based API that streams data back to the user as and when it is available. 
It queries the streaming recommendation endpoint of the recommendation backend described above. 
This endpoint is implemented as a fully asynchronous `HttpService` and can be queried using the following URI:

```
http://localhost:8085/recommendations/streaming?userId=1
```

### Asynchronous aggregated

This is an [endpoint](AggregatedGatewayService.java) that creates a single JSON array containing one or more
`FullRecommendation`s. Although the result is a single JSON array, the elements of the array are still fetched from the
other services asynchronously. 

This endpoint can be queried using the following endpoint:

```
http://localhost:8085/recommendations/aggregated?userId=1
```

### Blocking

This is an [endpoint](BlockingGatewayService.java) that uses blocking Http client and server APIs to sequentially query
all other services for each recommendation.

This endpoint can be queried using the following endpoint:

```
http://localhost:8085/recommendations/blocking?userId=1
```

## Missing ServiceTalk features.

This example demonstrates a complex usecase and few of the capabilities required are not available in ServiceTalk yet.
Below is a list of features that will soon be provided in ServiceTalk to remove boiler plate code from here:

- [ ] ServiceTalk `Executor` does not provide a way to do blocking execute, i.e. a way to execute task and return. This
makes it hard for blocking usecases to do timeouts since timeout requires another thread. Users can use JDK
`ExecutorService` but that means they have to manage threadpools at two places: ServiceTalk and `ExecutorService`.
- [ ] Timeout is a widely used operator that is missing.

package com.coremedia.subscriptions;

import graphql.schema.DataFetcher;
import reactor.core.publisher.Flux;

public class GraphQLDataFetchers {

  private final Flux<CoreMediaContentEvent> contentEventFlux;

  public GraphQLDataFetchers(Flux<CoreMediaContentEvent> contentEventFlux) {
    this.contentEventFlux = contentEventFlux;
  }

  public DataFetcher<Flux<CoreMediaContentEvent>> subscriptionFetcher() {
    return environment -> {
      return contentEventFlux.filter(item -> item.getContentId().startsWith("coremedia"));
    };
  }
}

package com.coremedia.subscriptions;

import com.coremedia.cap.content.ContentRepository;
import com.coremedia.cap.undoc.common.spring.CapRepositoriesConfiguration;
import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static java.lang.invoke.MethodHandles.lookup;

@Configuration
@EnableWebMvc
@Import({
        CapRepositoriesConfiguration.class,
})
@PropertySource(value = "classpath:/META-INF/coremedia/cap-client-component-defaults.properties", name = "cap-client-component-defaults.properties")
public class SubscriptionConfig implements WebMvcConfigurer {
  private static final Logger LOG = LoggerFactory.getLogger(lookup().lookupClass());

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    List<String> resources = new ArrayList<>(Arrays.asList("/static/**", "/docs/**"));
    List<String> resourceLocations = new ArrayList<>();
    resources.add("/graphiql/static/**");
    resourceLocations.add("classpath:/static/");
    resourceLocations.add("classpath:/META-INF/resources/");
    registry.addResourceHandler(resources.toArray(new String[0]))
            .addResourceLocations(resourceLocations.toArray(new String[0]));
  }

  @Bean
  public TypeDefinitionRegistry typeDefinitionRegistry()
          throws IOException {
    SchemaParser schemaParser = new SchemaParser();
    PathMatchingResourcePatternResolver loader = new PathMatchingResourcePatternResolver();
    StringBuilder stringBuilder = new StringBuilder();
    Resource[] resources = loader.getResources("classpath*:schema.graphqls");

    List<Resource> allResources = new ArrayList<>(Arrays.asList(resources));

    for (Resource resource : allResources) {
      LOG.info("Merging GraphQL schema {}", resource.getURI());
      try (InputStreamReader in = new InputStreamReader(resource.getInputStream())) {
        stringBuilder.append(IOUtils.toString(in));
      } catch (IOException e) {
        throw new IOException("There was an error while reading the schema file " + resource.getFilename(), e);
      }
    }
    return schemaParser.parse(stringBuilder.toString());
  }

  @Bean
  public GraphQLSchema graphQLSchema(RuntimeWiring wiring,
                                     TypeDefinitionRegistry typeRegistry) {
    SchemaGenerator schemaGenerator = new SchemaGenerator();
    return schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
  }

  @Bean
  public RuntimeWiring wiring(GraphQLDataFetchers graphQLDataFetchers) {
    return RuntimeWiring.newRuntimeWiring()
            .type(newTypeWiring("Subscription")
                    .dataFetcher("updatesFor", graphQLDataFetchers.subscriptionFetcher())
            )
            .build();
  }

  @Bean
  public GraphQL graphQL(GraphQLSchema graphQLSchema,
                         List<Instrumentation> instrumentations) {
    return GraphQL.newGraphQL(graphQLSchema)
            .instrumentation(new ChainedInstrumentation(instrumentations))
            .build();
  }

  @Bean
  public ContentInvalidationRepositoryListener contentInvalidationRepositoryListener(
          ContentRepository contentRepository,
          Sinks.Many<CoreMediaContentEvent> contentEventSink) {
    return new ContentInvalidationRepositoryListener(contentRepository, contentEventSink);
  }

  @Bean
  public Sinks.Many<CoreMediaContentEvent> contentEventSink() {
    return Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);
  }

  @Bean
  public Flux<CoreMediaContentEvent> contentEventFlux(Sinks.Many<CoreMediaContentEvent> contentEventSink) {
    return contentEventSink.asFlux();
  }

  @Bean
  public GraphQLDataFetchers graphQLDataFetchers(Flux<CoreMediaContentEvent> contentEventFlux) {
    return new GraphQLDataFetchers(contentEventFlux);
  }

}

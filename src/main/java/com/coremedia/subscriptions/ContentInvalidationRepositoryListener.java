package com.coremedia.subscriptions;

import com.coremedia.cap.content.Content;
import com.coremedia.cap.content.ContentRepository;
import com.coremedia.cap.content.Version;
import com.coremedia.cap.content.events.ContentEvent;
import com.coremedia.cap.content.events.ContentRepositoryListenerBase;
import com.coremedia.cap.content.events.PropertiesChangedEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.springframework.context.SmartLifecycle;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class ContentInvalidationRepositoryListener extends ContentRepositoryListenerBase implements SmartLifecycle {

  private static final Set<String> EVENT_WHITELIST = Set.of(
          ContentEvent.CONTENT_CREATED,
          ContentEvent.CONTENT_DELETED,
          ContentEvent.PROPERTIES_CHANGED,
          ContentEvent.CONTENT_REVERTED
  );

  private final AtomicBoolean running = new AtomicBoolean(false);

  private final ContentRepository repository;
  private final Sinks.Many<CoreMediaContentEvent> contentEventSink;

  ContentInvalidationRepositoryListener(ContentRepository repository, Sinks.Many<CoreMediaContentEvent> contentEventSink) {
    this.repository = repository;
    this.contentEventSink = contentEventSink;
  }

  @Override
  protected void handleContentEvent(@NonNull ContentEvent event) {
    if (!EVENT_WHITELIST.contains(event.getType())) {
      return;
    }

    Content content = event.getContent();
    if (content == null || content.isDestroyed() || !content.getType().isSubtypeOf("CMTeasable")) {
      return;
    }
    CoreMediaContentEvent coreMediaContentEvent = new CoreMediaContentEvent(content.getId(), event.getType());
    if (event instanceof PropertiesChangedEvent) {
      List<String> propertyNames = new ArrayList<>();
      PropertiesChangedEvent propertiesChangedEvent = (PropertiesChangedEvent) event;
      Version version = propertiesChangedEvent.getVersion();
      Version predecessor = version.getPredecessor();
      for (Map.Entry<String, Object> property : version.getProperties().entrySet()) {
        if (property.getValue() != null) {
          Object o = predecessor.getProperties().get(property.getKey());
          if (o != null && !o.equals(property.getValue())) {
            propertyNames.add(property.getKey());
          }
        }
      }
      coreMediaContentEvent.setPropertyNames(propertyNames);
    }
    contentEventSink.tryEmitNext(coreMediaContentEvent);
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public void stop(Runnable callback) {
    stop();
    callback.run();
  }

  @Override
  public void start() {
    if (!running.getAndSet(true)) {
      repository.addContentRepositoryListener(this);
    }
  }

  @Override
  public void stop() {
    if (running.getAndSet(false)) {
      repository.removeContentRepositoryListener(this);
    }
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public int getPhase() {
    return 0;
  }
}

package com.coremedia.subscriptions;

import java.util.List;

public class CoreMediaContentEvent {
  private String contentId;
  private String eventName;
  private List<String> propertyNames;

  public CoreMediaContentEvent(String contentId, String eventName) {
    this.contentId = contentId;
    this.eventName = eventName;
  }

  public String getContentId() {
    return contentId;
  }

  public void setContentId(String contentId) {
    this.contentId = contentId;
  }

  public String getEventName() {
    return eventName;
  }

  public void setEventName(String eventName) {
    this.eventName = eventName;
  }

  public List<String> getPropertyNames() {
    return propertyNames;
  }

  public void setPropertyNames(List<String> propertyNames) {
    this.propertyNames = propertyNames;
  }
}

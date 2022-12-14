package com.hubspot.baragon.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;

public class GraphiteConfiguration {
  @JsonProperty
  private boolean enabled = false;

  @JsonProperty
  private String hostname;

  @JsonProperty
  private int port = 2003;

  @JsonProperty
  @NotNull
  private String prefix = "";

  @JsonProperty
  @NotNull
  private String hostnameOmitSuffix = "";

  @JsonProperty
  private int periodSeconds = 60;

  @JsonProperty
  @NotNull
  private List<String> predicates = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getHostname() {
    return hostname;
  }

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public String getHostnameOmitSuffix() {
    return hostnameOmitSuffix;
  }

  public void setHostnameOmitSuffix(String hostnameOmitSuffix) {
    this.hostnameOmitSuffix = hostnameOmitSuffix;
  }

  public int getPeriodSeconds() {
    return periodSeconds;
  }

  public void setPeriodSeconds(int periodSeconds) {
    this.periodSeconds = periodSeconds;
  }

  public List<String> getPredicates() {
    return predicates;
  }

  public void setPredicates(List<String> predicates) {
    this.predicates = predicates;
  }
}

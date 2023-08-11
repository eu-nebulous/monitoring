package eu.melodic.models.interfaces;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.lang.Object;
import java.lang.String;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "metric",
    "component",
    "sensor",
    "sinks",
    "tags"
})
public class MonitorImpl implements Monitor {
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  @JsonProperty("metric")
  private String metric;

  @JsonProperty("component")
  private String component;

  @JsonProperty("sensor")
  private Sensor sensor;

  @JsonProperty("sinks")
  private List<Sink> sinks;

  @JsonProperty("tags")
  private List<KeyValuePair> tags;

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperties(Map<String, Object> additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  @JsonProperty("metric")
  public String getMetric() {
    return this.metric;
  }

  @JsonProperty("metric")
  public void setMetric(String metric) {
    this.metric = metric;
  }

  @JsonProperty("component")
  public String getComponent() {
    return this.component;
  }

  @JsonProperty("component")
  public void setComponent(String component) {
    this.component = component;
  }

  @JsonProperty("sensor")
  public Sensor getSensor() {
    return this.sensor;
  }

  @JsonProperty("sensor")
  public void setSensor(Sensor sensor) {
    this.sensor = sensor;
  }

  @JsonProperty("sinks")
  public List<Sink> getSinks() {
    return this.sinks;
  }

  @JsonProperty("sinks")
  public void setSinks(List<Sink> sinks) {
    this.sinks = sinks;
  }

  @JsonProperty("tags")
  public List<KeyValuePair> getTags() {
    return this.tags;
  }

  @JsonProperty("tags")
  public void setTags(List<KeyValuePair> tags) {
    this.tags = tags;
  }
}

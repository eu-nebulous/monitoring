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
    "className",
    "configuration",
    "interval"
})
public class PullSensorImpl implements PullSensor {
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  @JsonProperty("className")
  private String className;

  @JsonProperty("configuration")
  private List<KeyValuePair> configuration;

  @JsonProperty("interval")
  private Interval interval;

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperties(Map<String, Object> additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  @JsonProperty("className")
  public String getClassName() {
    return this.className;
  }

  @JsonProperty("className")
  public void setClassName(String className) {
    this.className = className;
  }

  @JsonProperty("configuration")
  public List<KeyValuePair> getConfiguration() {
    return this.configuration;
  }

  @JsonProperty("configuration")
  public void setConfiguration(List<KeyValuePair> configuration) {
    this.configuration = configuration;
  }

  @JsonProperty("interval")
  public Interval getInterval() {
    return this.interval;
  }

  @JsonProperty("interval")
  public void setInterval(Interval interval) {
    this.interval = interval;
  }
}

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
    "type",
    "configuration"
})
public class SinkImpl implements Sink {
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  @JsonProperty("type")
  private TypeType type;

  @JsonProperty("configuration")
  private List<KeyValuePair> configuration;

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperties(Map<String, Object> additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  @JsonProperty("type")
  public TypeType getType() {
    return this.type;
  }

  @JsonProperty("type")
  public void setType(TypeType type) {
    this.type = type;
  }

  @JsonProperty("configuration")
  public List<KeyValuePair> getConfiguration() {
    return this.configuration;
  }

  @JsonProperty("configuration")
  public void setConfiguration(List<KeyValuePair> configuration) {
    this.configuration = configuration;
  }
}

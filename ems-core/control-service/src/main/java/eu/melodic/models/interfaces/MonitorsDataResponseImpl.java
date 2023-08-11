package eu.melodic.models.interfaces;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import eu.melodic.models.commons.Watermark;

import java.lang.Object;
import java.lang.String;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "monitors",
    "watermark"
})
public class MonitorsDataResponseImpl implements MonitorsDataResponse {
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  @JsonProperty("monitors")
  private List<Monitor> monitors;

  @JsonProperty("watermark")
  private Watermark watermark;

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperties(Map<String, Object> additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  @JsonProperty("monitors")
  public List<Monitor> getMonitors() {
    return this.monitors;
  }

  @JsonProperty("monitors")
  public void setMonitors(List<Monitor> monitors) {
    this.monitors = monitors;
  }

  @JsonProperty("watermark")
  public Watermark getWatermark() {
    return this.watermark;
  }

  @JsonProperty("watermark")
  public void setWatermark(Watermark watermark) {
    this.watermark = watermark;
  }
}

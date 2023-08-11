package eu.melodic.models.services;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import eu.melodic.models.commons.NotificationResult;
import eu.melodic.models.commons.Watermark;

import java.lang.Object;
import java.lang.String;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "applicationId",
    "result",
    "watermark"
})
public class CamelModelNotificationRequestImpl implements CamelModelNotificationRequest {
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  @JsonProperty("applicationId")
  private String applicationId;

  @JsonProperty("result")
  private NotificationResult result;

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

  @JsonProperty("applicationId")
  public String getApplicationId() {
    return this.applicationId;
  }

  @JsonProperty("applicationId")
  public void setApplicationId(String applicationId) {
    this.applicationId = applicationId;
  }

  @JsonProperty("result")
  public NotificationResult getResult() {
    return this.result;
  }

  @JsonProperty("result")
  public void setResult(NotificationResult result) {
    this.result = result;
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

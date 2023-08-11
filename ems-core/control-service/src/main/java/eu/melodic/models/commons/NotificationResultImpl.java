package eu.melodic.models.commons;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.lang.Object;
import java.lang.String;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "status",
    "errorCode",
    "errorDescription"
})
public class NotificationResultImpl implements NotificationResult {
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  @JsonProperty("status")
  private StatusType status;

  @JsonProperty("errorCode")
  private String errorCode;

  @JsonProperty("errorDescription")
  private String errorDescription;

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperties(Map<String, Object> additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  @JsonProperty("status")
  public StatusType getStatus() {
    return this.status;
  }

  @JsonProperty("status")
  public void setStatus(StatusType status) {
    this.status = status;
  }

  @JsonProperty("errorCode")
  public String getErrorCode() {
    return this.errorCode;
  }

  @JsonProperty("errorCode")
  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  @JsonProperty("errorDescription")
  public String getErrorDescription() {
    return this.errorDescription;
  }

  @JsonProperty("errorDescription")
  public void setErrorDescription(String errorDescription) {
    this.errorDescription = errorDescription;
  }
}

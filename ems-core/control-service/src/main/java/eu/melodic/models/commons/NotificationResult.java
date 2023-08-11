package eu.melodic.models.commons;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.lang.Object;
import java.lang.String;
import java.util.Map;

@JsonDeserialize(
    as = NotificationResultImpl.class
)
public interface NotificationResult {
  Map<String, Object> getAdditionalProperties();

  void setAdditionalProperties(Map<String, Object> additionalProperties);

  StatusType getStatus();

  void setStatus(StatusType status);

  String getErrorCode();

  void setErrorCode(String errorCode);

  String getErrorDescription();

  void setErrorDescription(String errorDescription);

  enum StatusType {
    @JsonProperty("SUCCESS")
    SUCCESS("SUCCESS"),

    @JsonProperty("ERROR")
    ERROR("ERROR");

    private String name;

    StatusType(String name) {
      this.name = name;
    }
  }
}

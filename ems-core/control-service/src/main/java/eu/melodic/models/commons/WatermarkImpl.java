package eu.melodic.models.commons;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.lang.Object;
import java.lang.String;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "user",
    "system",
    "date",
    "uuid"
})
public class WatermarkImpl implements Watermark {
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  @JsonProperty("user")
  private String user;

  @JsonProperty("system")
  private String system;

  @JsonProperty("date")
  @JsonFormat(
      shape = JsonFormat.Shape.STRING,
      pattern = "yyyy-MM-dd'T'HH:mm:ssZ"
  )
  private Date date;

  @JsonProperty("uuid")
  private String uuid;

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperties(Map<String, Object> additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  @JsonProperty("user")
  public String getUser() {
    return this.user;
  }

  @JsonProperty("user")
  public void setUser(String user) {
    this.user = user;
  }

  @JsonProperty("system")
  public String getSystem() {
    return this.system;
  }

  @JsonProperty("system")
  public void setSystem(String system) {
    this.system = system;
  }

  @JsonProperty("date")
  public Date getDate() {
    return this.date;
  }

  @JsonProperty("date")
  public void setDate(Date date) {
    this.date = date;
  }

  @JsonProperty("uuid")
  public String getUuid() {
    return this.uuid;
  }

  @JsonProperty("uuid")
  public void setUuid(String uuid) {
    this.uuid = uuid;
  }
}

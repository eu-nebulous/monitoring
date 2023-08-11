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
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "unit",
    "period"
})
public class IntervalImpl implements Interval {
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  @JsonProperty("unit")
  private UnitType unit;

  @JsonProperty("period")
  private int period;

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperties(Map<String, Object> additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  @JsonProperty("unit")
  public UnitType getUnit() {
    return this.unit;
  }

  @JsonProperty("unit")
  public void setUnit(UnitType unit) {
    this.unit = unit;
  }

  @JsonProperty("period")
  public int getPeriod() {
    return this.period;
  }

  @JsonProperty("period")
  public void setPeriod(int period) {
    this.period = period;
  }
}

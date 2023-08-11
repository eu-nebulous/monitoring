package eu.melodic.models.interfaces;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.lang.Object;
import java.lang.String;
import java.util.Map;

@JsonDeserialize(
    as = IntervalImpl.class
)
public interface Interval {
  Map<String, Object> getAdditionalProperties();

  void setAdditionalProperties(Map<String, Object> additionalProperties);

  UnitType getUnit();

  void setUnit(UnitType unit);

  int getPeriod();

  void setPeriod(int period);

  enum UnitType {
    @JsonProperty("DAYS")
    DAYS("DAYS"),

    @JsonProperty("HOURS")
    HOURS("HOURS"),

    @JsonProperty("MICROSECONDS")
    MICROSECONDS("MICROSECONDS"),

    @JsonProperty("MILLISECONDS")
    MILLISECONDS("MILLISECONDS"),

    @JsonProperty("MINUTES")
    MINUTES("MINUTES"),

    @JsonProperty("NANOSECONDS")
    NANOSECONDS("NANOSECONDS"),

    @JsonProperty("SECONDS")
    SECONDS("SECONDS");

    private String name;

    UnitType(String name) {
      this.name = name;
    }
  }
}

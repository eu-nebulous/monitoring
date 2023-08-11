package eu.melodic.models.interfaces;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.lang.Object;
import java.lang.String;
import java.util.List;
import java.util.Map;

@JsonDeserialize(
    as = SinkImpl.class
)
public interface Sink {
  Map<String, Object> getAdditionalProperties();

  void setAdditionalProperties(Map<String, Object> additionalProperties);

  TypeType getType();

  void setType(TypeType type);

  List<KeyValuePair> getConfiguration();

  void setConfiguration(List<KeyValuePair> configuration);

  enum TypeType {
    @JsonProperty("KAIROS_DB")
    KAIROSDB("KAIROS_DB"),

    @JsonProperty("INFLUX")
    INFLUX("INFLUX"),

    @JsonProperty("JMS")
    JMS("JMS"),

    @JsonProperty("CLI")
    CLI("CLI");

    private String name;

    TypeType(String name) {
      this.name = name;
    }
  }
}

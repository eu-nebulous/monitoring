package eu.melodic.models.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.lang.Object;
import java.lang.String;
import java.util.Map;

@JsonDeserialize(
    as = KeyValuePairImpl.class
)
public interface KeyValuePair {
  Map<String, Object> getAdditionalProperties();

  void setAdditionalProperties(Map<String, Object> additionalProperties);

  String getKey();

  void setKey(String key);

  String getValue();

  void setValue(String value);
}

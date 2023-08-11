package eu.melodic.models.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.lang.Object;
import java.lang.String;
import java.util.List;
import java.util.Map;

@JsonDeserialize(
    as = PullSensorImpl.class
)
public interface PullSensor {
  Map<String, Object> getAdditionalProperties();

  void setAdditionalProperties(Map<String, Object> additionalProperties);

  String getClassName();

  void setClassName(String className);

  List<KeyValuePair> getConfiguration();

  void setConfiguration(List<KeyValuePair> configuration);

  Interval getInterval();

  void setInterval(Interval interval);
}

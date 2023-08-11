package eu.melodic.models.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.lang.Object;
import java.lang.String;
import java.util.List;
import java.util.Map;

@JsonDeserialize(
    as = MonitorImpl.class
)
public interface Monitor {
  Map<String, Object> getAdditionalProperties();

  void setAdditionalProperties(Map<String, Object> additionalProperties);

  String getMetric();

  void setMetric(String metric);

  String getComponent();

  void setComponent(String component);

  Sensor getSensor();

  void setSensor(Sensor sensor);

  List<Sink> getSinks();

  void setSinks(List<Sink> sinks);

  List<KeyValuePair> getTags();

  void setTags(List<KeyValuePair> tags);
}

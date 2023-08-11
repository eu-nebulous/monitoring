package eu.melodic.models.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.lang.Object;
import java.lang.String;
import java.util.Map;

@JsonDeserialize(
    as = PushSensorImpl.class
)
public interface PushSensor {
  Map<String, Object> getAdditionalProperties();

  void setAdditionalProperties(Map<String, Object> additionalProperties);

  int getPort();

  void setPort(int port);
}

package eu.melodic.models.commons;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.lang.Object;
import java.lang.String;
import java.util.Date;
import java.util.Map;

@JsonDeserialize(
    as = WatermarkImpl.class
)
public interface Watermark {
  Map<String, Object> getAdditionalProperties();

  void setAdditionalProperties(Map<String, Object> additionalProperties);

  String getUser();

  void setUser(String user);

  String getSystem();

  void setSystem(String system);

  Date getDate();

  void setDate(Date date);

  String getUuid();

  void setUuid(String uuid);
}

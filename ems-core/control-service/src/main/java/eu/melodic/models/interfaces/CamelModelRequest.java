package eu.melodic.models.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import eu.melodic.models.commons.Watermark;

import java.lang.Object;
import java.lang.String;
import java.util.Map;

@JsonDeserialize(
    as = CamelModelRequestImpl.class
)
public interface CamelModelRequest {
  Map<String, Object> getAdditionalProperties();

  void setAdditionalProperties(Map<String, Object> additionalProperties);

  String getApplicationId();

  void setApplicationId(String applicationId);

  String getNotificationURI();

  void setNotificationURI(String notificationURI);

  Watermark getWatermark();

  void setWatermark(Watermark watermark);
}

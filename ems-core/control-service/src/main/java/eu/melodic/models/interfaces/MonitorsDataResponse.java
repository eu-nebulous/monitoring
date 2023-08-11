package eu.melodic.models.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import eu.melodic.models.commons.Watermark;

import java.lang.Object;
import java.lang.String;
import java.util.List;
import java.util.Map;

@JsonDeserialize(
    as = MonitorsDataResponseImpl.class
)
public interface MonitorsDataResponse {
  Map<String, Object> getAdditionalProperties();

  void setAdditionalProperties(Map<String, Object> additionalProperties);

  List<Monitor> getMonitors();

  void setMonitors(List<Monitor> monitors);

  Watermark getWatermark();

  void setWatermark(Watermark watermark);
}

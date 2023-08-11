package eu.melodic.models.resources;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import gr.iccs.imu.ems.util.StrUtil;
import eu.melodic.models.interfaces.PullSensor;
import eu.melodic.models.interfaces.PushSensor;
import eu.melodic.models.interfaces.Sensor;
import java.io.IOException;
import java.lang.Object;
import java.lang.String;
import java.util.Arrays;
import java.util.Map;

public class SensorDeserializer extends StdDeserializer<Sensor> {
  public SensorDeserializer() {
    super(Sensor.class);}

  private boolean looksLikePushSensor(Map<String, Object> map) {
    return map.keySet().containsAll(Arrays.asList("port"));
  }

  private boolean looksLikePullSensor(Map<String, Object> map) {
    return map.keySet().containsAll(Arrays.asList("className","configuration","interval"));
  }

  public Sensor deserialize(JsonParser jsonParser, DeserializationContext jsonContext) throws IOException {
    ObjectMapper mapper  = new ObjectMapper();
    Map<String, Object> map = StrUtil.castToMapStringObject( mapper.readValue(jsonParser, Map.class) );
    if ( looksLikePushSensor(map) ) return new Sensor(mapper.convertValue(map, PushSensor.class));
    if ( looksLikePullSensor(map) ) return new Sensor(mapper.convertValue(map, PullSensor.class));
    throw new IOException("Can't figure out type of object" + map);
  }
}

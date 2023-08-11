package eu.melodic.models.resources;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import eu.melodic.models.interfaces.Sensor;
import java.io.IOException;

public class SensorSerializer extends StdSerializer<Sensor> {
  public SensorSerializer() {
    super(Sensor.class);}

  public void serialize(Sensor object, JsonGenerator jsonGenerator, SerializerProvider jsonSerializerProvider) throws IOException, JsonProcessingException {
    if ( object.isPushSensor()) {
      jsonGenerator.writeObject(object.getPushSensor());
      return;
    }
    if ( object.isPullSensor()) {
      jsonGenerator.writeObject(object.getPullSensor());
      return;
    }
    throw new IOException("Can't figure out type of object" + object);
  }
}

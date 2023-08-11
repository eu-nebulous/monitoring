package eu.melodic.models.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import eu.melodic.models.resources.SensorDeserializer;
import eu.melodic.models.resources.SensorSerializer;

import java.lang.IllegalStateException;
import java.lang.Object;

@JsonDeserialize(
    using = SensorDeserializer.class
)
@JsonSerialize(
    using = SensorSerializer.class
)
public class Sensor {
  private Object anyType;

  private Sensor() {
    this.anyType = null;
  }

  public Sensor(PushSensor pushSensor) {
    this.anyType = pushSensor;
  }

  public Sensor(PullSensor pullSensor) {
    this.anyType = pullSensor;
  }

  public PushSensor getPushSensor() {
    if ( !(anyType instanceof  PushSensor)) throw new IllegalStateException("fetching wrong type out of the union: PushSensor");
    return (PushSensor) anyType;
  }

  public boolean isPushSensor() {
    return anyType instanceof PushSensor;
  }

  public PullSensor getPullSensor() {
    if ( !(anyType instanceof  PullSensor)) throw new IllegalStateException("fetching wrong type out of the union: PullSensor");
    return (PullSensor) anyType;
  }

  public boolean isPullSensor() {
    return anyType instanceof PullSensor;
  }
}

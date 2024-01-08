/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.event;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import gr.iccs.imu.ems.brokercep.properties.BrokerCepProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.scheduling.TaskScheduler;

import jakarta.jms.*;
import jakarta.jms.Queue;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.lang.IllegalStateException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class EventRecorder extends LinkedHashMap<String, Object> implements Runnable {
    public enum FORMAT { JSON, CSV }

    private final static Object staticLock = new Object();
    public static Set<EventRecorder> activeEventRecorders;

    @Getter
    private final FORMAT recordFormat;
    @Getter
    private final String recordFilePattern;
    @Getter
    private final BrokerCepProperties.EVENT_RECORDER_FILTER_MODE filterMode;
    @Getter
    private final List<String> allowedDestinations;

    @Getter
    private String recordFile;
    @Getter
    private boolean closed;
    @Getter
    private boolean recording;

    private BufferedWriter recordWriter;
    private CSVPrinter csvPrinter;
    private JsonGenerator jsonGenerator;

    private final Deque<Message> eventQueue;
    private final TaskScheduler scheduler;
    private ScheduledFuture<?> runnerFuture;

    public EventRecorder(@NonNull BrokerCepProperties.EventRecorderProperties properties, @NonNull TaskScheduler scheduler) throws IOException {
        this(properties.getFormat(), properties.getFile(), properties.getFilterMode(), properties.getAllowedDestinations(), scheduler);
    }

    public EventRecorder(@NonNull FORMAT recordFormat, @NonNull String recordFilePattern, BrokerCepProperties.EVENT_RECORDER_FILTER_MODE filterMode, List<String> allowedDestinations, @NonNull TaskScheduler scheduler) throws IOException {
        this.recordFormat = recordFormat;
        this.recordFilePattern = recordFilePattern;
        this.filterMode = filterMode;
        this.allowedDestinations = allowedDestinations==null ? Collections.emptyList() : Collections.unmodifiableList(allowedDestinations);
        this.scheduler = scheduler;
        this.eventQueue = new ConcurrentLinkedDeque<>();

        registerShutdownHook();
        rotate();
    }

    public static void registerShutdownHook() {
        if (activeEventRecorders==null) {
            synchronized (staticLock) {
                if (activeEventRecorders==null) {
                    activeEventRecorders = new HashSet<>();
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        log.info("EventRecorder: closing active recorders: {}", activeEventRecorders.size());
                        for (EventRecorder eventRecorder : activeEventRecorders) {
                            if (!eventRecorder.isClosed())
                                eventRecorder.close();
                        }
                        log.info("EventRecorder: Closed active recorders");
                    }));
                }
            }
        }
    }

    public synchronized void rotate() throws IOException {
        // Close current recording file
        if (recordFile!=null && !isClosed()) {
            close();
        }
        closed = false;

        // Create new recording file
        this.recordFile = recordFilePattern
                .replace("%T", "" + System.currentTimeMillis())
                .replace("%S", getSuffix());
        this.recordWriter = new BufferedWriter(new FileWriter(recordFile));

        //log.info("EventRecorder: Record format: {}", recordFormat);
        //log.info("EventRecorder: Record file:   {}", recordFile);
        log.info("EventRecorder: Record format: {},  Record file: {}", recordFormat, recordFile);

        if (recordFormat==FORMAT.CSV) {
            csvPrinter = new CSVPrinter(recordWriter, CSVFormat.DEFAULT
                    .withHeader("Timestamp", "Destination", "Mime", "Type", "Contents", "Properties"));
            csvPrinter.flush();
        }
        if (recordFormat==FORMAT.JSON) {
            jsonGenerator = new JsonFactory()
                    .createGenerator(recordWriter)
                    .setPrettyPrinter(new DefaultPrettyPrinter());
            jsonGenerator.writeStartArray();
            jsonGenerator.flush();
        }

        // Start processing loop
        runnerFuture = scheduler.scheduleAtFixedRate(this, Duration.ofMillis(1000));
        activeEventRecorders.add(this);

        startRecording();
    }

    private String getSuffix() {
        if (recordFormat==FORMAT.JSON) return "json";
        if (recordFormat==FORMAT.CSV) return "csv";
        throw new IllegalStateException("No suffix for FORMAT: "+recordFormat);
    }

    public synchronized void close() {
        if (closed) throw new IllegalStateException("EventRecorder has already been closed");
        if (recording) stopRecording();
        this.closed = true;
        runnerFuture.cancel(false);
        activeEventRecorders.remove(this);

        // wait until all records are written in the file
        while (!eventQueue.isEmpty()) {
            run();
        }

        // close record file
        try {
            if (recordFormat == FORMAT.CSV) {
                csvPrinter.close(true);
            }
            if (recordFormat == FORMAT.JSON) {
                jsonGenerator.writeEndArray();
                jsonGenerator.close();
            }
            recordWriter.close();
        } catch (Exception ex) {
            log.warn("EventRecorder: Exception while closing: ", ex);
        }
    }

    public void startRecording() {
        if (closed) throw new IllegalStateException("EventRecorder has been closed");
        if (!recording) {
            log.info("EventRecorder: Start recording...");
            recording = true;
        }
    }

    public void stopRecording() {
        if (closed) throw new IllegalStateException("EventRecorder has been closed");
        if (recording) {
            log.info("EventRecorder: Stop recording...");
            recording = false;
        }
    }

    public void recordEvent(@NonNull ActiveMQMessage message) throws JMSException {
        recordAllowedEvent(message);
    }

    public void recordAllowedEvent(@NonNull Message message) throws JMSException {
        if (filterMode == BrokerCepProperties.EVENT_RECORDER_FILTER_MODE.ALL
            || filterMode == BrokerCepProperties.EVENT_RECORDER_FILTER_MODE.ALLOWED
                && allowedDestinations.stream().anyMatch(getDestinationName(message)::equalsIgnoreCase))
        {
            eventQueue.addLast(message);
        }
    }

    public void recordRegisteredEvent(@NonNull Message message) {
        if (filterMode==BrokerCepProperties.EVENT_RECORDER_FILTER_MODE.REGISTERED) {
            eventQueue.addLast(message);
        }
    }

    public void run() {
        if (!closed) {
            while (!eventQueue.isEmpty()) {
                try {
                    processEvent(eventQueue.removeLast());
                } catch (Exception ex) {
                    log.warn("EventRecorder: Exception while processing event queue: ", ex);
                }
            }
        }
    }

    protected void processEvent(Message message) throws IOException, JMSException {
        String messageId = message.getJMSMessageID();
        long timestamp = message.getJMSTimestamp();
        String destinationName = getDestinationName(message);
        String mime = message.getJMSType();

        // Extract event payload and type
        PayloadAndType payloadAndType = extractPayloadAndType(message);
        String content = payloadAndType.payload;
        String type = payloadAndType.type;

        // Extract event properties
        String properties = extractProperties(message);

        if (recordFormat==FORMAT.CSV) {
            csvPrinter.printRecord(timestamp, destinationName, mime, type, content, properties);
            csvPrinter.flush();
        }
        if (recordFormat==FORMAT.JSON) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("id", messageId);
            jsonGenerator.writeNumberField("timestamp", timestamp);
            jsonGenerator.writeStringField("destination", destinationName);
            jsonGenerator.writeStringField("mime", mime);
            jsonGenerator.writeStringField("type", type);
            jsonGenerator.writeStringField("content", content);
            jsonGenerator.writeStringField("properties", properties);
            jsonGenerator.writeEndObject();
            jsonGenerator.flush();
        }
    }

    protected String getDestinationName(Message message) throws JMSException {
        Destination d = message.getJMSDestination();
        if (d instanceof Topic topic) {
            return topic.getTopicName();
        } else
        if (d instanceof Queue queue) {
            return queue.getQueueName();
        } else
            throw new IllegalArgumentException("Argument is not a JMS destination: "+d);
    }

    protected PayloadAndType extractPayloadAndType(Message message) throws JMSException {
        if (message instanceof TextMessage textMessage) {
            return new PayloadAndType("TEXT", textMessage.getText());
        } else
        if (message instanceof ObjectMessage objectMessage) {
            Serializable o = objectMessage.getObject();
            return new PayloadAndType("OBJECT", o==null ? null : o.toString());
        } else
            throw new IllegalArgumentException("Unsupported message type: "+message.getClass().getName());
    }

    protected String extractProperties(Message message) throws JMSException {
        Enumeration en = message.getPropertyNames();
        StringBuilder properties = new StringBuilder("{");
        boolean first = true;
        while (en.hasMoreElements()) {
            Object k = en.nextElement();
            if (k!=null) {
                String v = message.getStringProperty(k.toString());
                if (first) first = false; else properties.append(", ");
                properties.append(k).append("=").append(v);
            }
        }
        properties.append(" }");
        return properties.toString();
    }

    @AllArgsConstructor
    class PayloadAndType {
        public String type;
        public String payload;
    }
}
var BrokerClient = Packages.gr.iccs.imu.ems.brokerclient.BrokerClient;
var System = Packages.java.lang.System;

var sleep = Packages.java.lang.Thread.sleep;
var currentTimeMillis = Packages.java.lang.System.currentTimeMillis;
var newClient = Packages.gr.iccs.imu.ems.brokerclient.BrokerClient.newClient;
var getMessageListener = Packages.gr.iccs.imu.ems.brokerclient.BrokerClientApp.getMessageListener;

print("Time: "+currentTimeMillis());
print("Args: "+args);

var topic = argsArray[0];
var value = parseFloat(argsArray[1]);
var url = argsArray.length> 2 ? argsArray[2] : 'tcp://localhost:61616';
var username = argsArray.length > 3 ? argsArray[3] : '';
var password = argsArray.length > 4 ? argsArray[4] : '';

main();

function main() {
    var bc = newClient(username, password);
    bc.getClientProperties().setPreserveConnection(true);
    //print("BC: "+bc.getClientProperties());

    list_topics(bc, url);

    print(`Subscribing for events to topic ${topic}...`);
    var listener = getMessageListener('', '');
    var exit_callback = null;
    bc.subscribe(url, topic, listener);

    print("Publishing events...");
    bc.publishEvent(url, topic, { "metricValue": value, "level": 1, "timestamp": currentTimeMillis() }, { 'AAA': 'aaa', 'BBB': 'bbb' });
    bc.publishEvent(url, topic, 'MAP', '{ "command": "do something" }', { 'AAA': 'aaa', 'BBB': 'bbb' });

    sleep(2 * 1000);
    list_topics(bc, url);

    bc.unsubscribe(listener);
    bc.closeConnection();
}

function list_topics(bc, url) {
    print();
    print('Topics at: ' + url);
    for (s of bc.getDestinationNamesSorted(url)) {
        print('  '+s);
    }
    print();
}

import time
from java.lang import Object
from java.lang import System
from gr.iccs.imu.ems.brokerclient import BrokerClient
from gr.iccs.imu.ems.brokerclient import BrokerClientApp
from gr.iccs.imu.ems.brokerclient.event import EventMap
from gr.iccs.imu.ems.brokerclient.BrokerClient import MESSAGE_TYPE
from gr.iccs.imu.ems.brokerclient.BrokerClient import ON_EXCEPTION

topic = args[0]
value = float(args[1])
url = args[2] if len(args) > 2 else 'tcp://localhost:61616'
username = args[3] if len(args) > 3 else ''
password = args[4] if len(args) > 4 else ''

bc = BrokerClient.newClient(username, password)
bc.getClientProperties().setPreserveConnection(1)

def main():
    list_topics()

    print "Subscribing for events to topic %s..." % topic
    # listener = lambda message: System.out.println("Received: %s - %d" % message.getClass().getSimpleName(), message.getBody(Object.class))
    listener = BrokerClientApp.getMessageListener('', '')
    exit_callback = lambda exit_code: None
    bc.subscribe(url, topic, listener)
    # bc.subscribeWithAutoReconnect(url, topic, listener, ON_EXCEPTION.LOG_AND_IGNORE, exit_callback)

    print "Publishing events..."
    bc.publishEvent(url, topic, MESSAGE_TYPE.TEXT, EventMap(value, 1, System.currentTimeMillis()), {'PPPP': '1111', 'RRRRR': '22222'})
    bc.publishEvent(url, topic, 'MAP', '{ "command": "do something" }', { 'AAA': 'aaa', 'BBB': 'bbb' })
    # BrokerClientApp.sendEvent(url, username, password, topic, 'MAP', '{ "command": "do something" }', { 'AAA': 'aaa', 'BBB': 'bbb' })

    time.sleep(2)
    list_topics()

    bc.unsubscribe(listener)
    bc.closeConnection()


def list_topics():
    print
    print "Topics at: ", url
    for s in sorted(bc.getDestinationNames(url)):
        print(s)
    print

main()

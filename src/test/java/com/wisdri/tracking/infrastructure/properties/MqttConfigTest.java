package com.wisdri.tracking.infrastructure.properties;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttConfigTest {
    @Test
    void enablesPahoAutomaticReconnectByDefaultAndAllowsDisablingIt() {
        MqttConfig config = new MqttConfig();
        config.setUrl("tcp://localhost:1883");

        DefaultMqttPahoClientFactory enabledFactory = config.trackingMqttClientFactory();
        assertTrue(enabledFactory.getConnectionOptions().isAutomaticReconnect());

        config.setAutomaticReconnect(false);
        MqttConnectOptions disabledOptions = config.trackingMqttClientFactory().getConnectionOptions();
        assertFalse(disabledOptions.isAutomaticReconnect());
    }
}

package br.arthconf.fortivus.fire_event_service.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String FIRE_EVENTS_EXCHANGE = "fortivus.fire_events";
    public static final String ROUTING_KEY_CRITICAL = "fire.detected.severe";

    @Bean
    public TopicExchange fireEventsExchange() {
        return new TopicExchange(FIRE_EVENTS_EXCHANGE);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

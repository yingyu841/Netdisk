package com.netdisk.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class RabbitMqPublisherReliabilityConfig implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {
    private static final Logger log = LoggerFactory.getLogger("biz.chat.mq.publisher");

    private final RabbitTemplate rabbitTemplate;
    private final Counter confirmAckCounter;
    private final Counter confirmNackCounter;
    private final Counter returnCounter;

    public RabbitMqPublisherReliabilityConfig(RabbitTemplate rabbitTemplate, MeterRegistry meterRegistry) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmAckCounter = Counter.builder("netdisk.rabbitmq.publisher.confirm.ack.total")
                .description("RabbitMQ publisher confirm ack total")
                .register(meterRegistry);
        this.confirmNackCounter = Counter.builder("netdisk.rabbitmq.publisher.confirm.nack.total")
                .description("RabbitMQ publisher confirm nack total")
                .register(meterRegistry);
        this.returnCounter = Counter.builder("netdisk.rabbitmq.publisher.return.total")
                .description("RabbitMQ publisher return total")
                .register(meterRegistry);
    }

    @PostConstruct
    public void initCallbacks() {
        rabbitTemplate.setConfirmCallback(this);
        rabbitTemplate.setReturnsCallback(this);
    }

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        String cid = correlationData == null ? "" : String.valueOf(correlationData.getId());
        if (ack) {
            confirmAckCounter.increment();
            return;
        }
        confirmNackCounter.increment();
        log.error("rabbitmq publish confirm nack correlationId={} cause={}", cid, cause == null ? "" : cause);
    }

    @Override
    public void returnedMessage(ReturnedMessage returned) {
        returnCounter.increment();
        if (returned == null) {
            return;
        }
        log.error(
                "rabbitmq message returned exchange={} routingKey={} replyCode={} replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()
        );
    }
}

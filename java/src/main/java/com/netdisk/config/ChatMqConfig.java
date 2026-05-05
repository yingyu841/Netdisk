package com.netdisk.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "app.chat-mq", name = "enabled", havingValue = "true")
public class ChatMqConfig {

    @Bean
    public TopicExchange chatTopicExchange(AppProperties appProperties) {
        return new TopicExchange(appProperties.getChatMq().getExchange(), true, false);
    }

    @Bean
    public Queue chatMessageSentQueue(AppProperties appProperties) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("x-dead-letter-exchange", appProperties.getChatMq().getDeadExchange());
        args.put("x-dead-letter-routing-key", appProperties.getChatMq().getDeadRoutingKey());
        return new Queue(appProperties.getChatMq().getQueue(), true, false, false, args);
    }

    @Bean
    public Queue chatReadBatchQueue(AppProperties appProperties) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("x-dead-letter-exchange", appProperties.getChatMq().getDeadExchange());
        args.put("x-dead-letter-routing-key", appProperties.getChatMq().getDeadRoutingKey());
        return new Queue(appProperties.getChatMq().getReadBatchQueue(), true, false, false, args);
    }

    @Bean
    public Binding chatMessageSentBinding(
            Queue chatMessageSentQueue,
            TopicExchange chatTopicExchange,
            AppProperties appProperties) {
        return BindingBuilder.bind(chatMessageSentQueue)
                .to(chatTopicExchange)
                .with(appProperties.getChatMq().getRoutingKey());
    }

    @Bean
    public Binding chatReadBatchBinding(
            Queue chatReadBatchQueue,
            TopicExchange chatTopicExchange,
            AppProperties appProperties) {
        return BindingBuilder.bind(chatReadBatchQueue)
                .to(chatTopicExchange)
                .with(appProperties.getChatMq().getReadBatchRoutingKey());
    }

    @Bean
    public TopicExchange chatRetryExchange(AppProperties appProperties) {
        return new TopicExchange(appProperties.getChatMq().getRetryExchange(), true, false);
    }

    @Bean
    public Queue chatRetryQueue(AppProperties appProperties) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("x-message-ttl", appProperties.getChatMq().getRetryDelayMs());
        args.put("x-dead-letter-exchange", appProperties.getChatMq().getExchange());
        return new Queue(appProperties.getChatMq().getRetryQueue(), true, false, false, args);
    }

    @Bean
    public Binding chatRetryBinding(
            Queue chatRetryQueue,
            TopicExchange chatRetryExchange,
            AppProperties appProperties) {
        return BindingBuilder.bind(chatRetryQueue)
                .to(chatRetryExchange)
                .with(appProperties.getChatMq().getRetryRoutingKey());
    }

    @Bean
    public TopicExchange chatDeadExchange(AppProperties appProperties) {
        return new TopicExchange(appProperties.getChatMq().getDeadExchange(), true, false);
    }

    @Bean
    public Queue chatDeadQueue(AppProperties appProperties) {
        return new Queue(appProperties.getChatMq().getDeadQueue(), true);
    }

    @Bean
    public Binding chatDeadBinding(
            Queue chatDeadQueue,
            TopicExchange chatDeadExchange,
            AppProperties appProperties) {
        return BindingBuilder.bind(chatDeadQueue)
                .to(chatDeadExchange)
                .with(appProperties.getChatMq().getDeadRoutingKey());
    }
}

package com.netdisk.messaging.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netdisk.config.AppProperties;
import com.netdisk.service.ChatMessageAsyncService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ChatMessageEventListenerTest {

    @Test
    public void shouldSendToDeadWhenBodyIsInvalidJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMessageAsyncService asyncService = Mockito.mock(ChatMessageAsyncService.class);
        ChatMessageEventPublisher publisher = Mockito.mock(ChatMessageEventPublisher.class);
        AppProperties properties = buildProperties();
        Mockito.when(publisher.publishDead(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyString())).thenReturn(true);

        ChatMessageEventListener listener = new ChatMessageEventListener(objectMapper, asyncService, publisher, properties);
        listener.onMessageSent("{invalid-json", null, null);

        Mockito.verify(asyncService, Mockito.never()).processMessageSent(Mockito.any(ChatMessageSentEvent.class));
        Mockito.verify(publisher, Mockito.never()).publishRetry(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt());
        Mockito.verify(publisher, Mockito.times(1)).publishDead(Mockito.anyString(), Mockito.anyString(), Mockito.eq(0), Mockito.anyString());
    }

    @Test
    public void shouldRetryWhenConsumeThrowsRetryableError() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMessageAsyncService asyncService = Mockito.mock(ChatMessageAsyncService.class);
        ChatMessageEventPublisher publisher = Mockito.mock(ChatMessageEventPublisher.class);
        AppProperties properties = buildProperties();
        Mockito.when(publisher.publishRetry(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt())).thenReturn(true);
        Mockito.doThrow(new RuntimeException("transient")).when(asyncService).processMessageSent(Mockito.any(ChatMessageSentEvent.class));

        ChatMessageEventListener listener = new ChatMessageEventListener(objectMapper, asyncService, publisher, properties);
        ChatMessageSentEvent event = new ChatMessageSentEvent();
        event.setEventId("evt-1");
        event.setConversationId(1L);
        event.setConversationUuid("conv-1");
        event.setMessageId(2L);
        event.setMessageUuid("msg-1");
        event.setSenderUserId(3L);
        event.setMessageType("text");
        String body = objectMapper.writeValueAsString(event);

        listener.onMessageSent(body, 0, properties.getChatMq().getRoutingKey());

        Mockito.verify(asyncService, Mockito.times(1)).processMessageSent(Mockito.any(ChatMessageSentEvent.class));
        Mockito.verify(publisher, Mockito.times(1)).publishRetry(Mockito.eq(properties.getChatMq().getRoutingKey()), Mockito.eq(body), Mockito.eq(1));
        Mockito.verify(publisher, Mockito.never()).publishDead(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyString());
    }

    @Test
    public void shouldDeadWhenRetryExhausted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMessageAsyncService asyncService = Mockito.mock(ChatMessageAsyncService.class);
        ChatMessageEventPublisher publisher = Mockito.mock(ChatMessageEventPublisher.class);
        AppProperties properties = buildProperties();
        Mockito.when(publisher.publishDead(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyString())).thenReturn(true);
        Mockito.doThrow(new RuntimeException("still failing")).when(asyncService).processMessageSent(Mockito.any(ChatMessageSentEvent.class));

        ChatMessageEventListener listener = new ChatMessageEventListener(objectMapper, asyncService, publisher, properties);
        ChatMessageSentEvent event = new ChatMessageSentEvent();
        event.setEventId("evt-2");
        event.setConversationId(11L);
        event.setConversationUuid("conv-11");
        event.setMessageId(22L);
        event.setMessageUuid("msg-22");
        event.setSenderUserId(33L);
        event.setMessageType("text");
        String body = objectMapper.writeValueAsString(event);

        listener.onMessageSent(body, properties.getChatMq().getMaxRetry(), properties.getChatMq().getRoutingKey());

        Mockito.verify(publisher, Mockito.never()).publishRetry(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt());
        Mockito.verify(publisher, Mockito.times(1)).publishDead(
                Mockito.eq(properties.getChatMq().getRoutingKey()),
                Mockito.eq(body),
                Mockito.eq(properties.getChatMq().getMaxRetry()),
                Mockito.anyString()
        );
    }

    private AppProperties buildProperties() {
        AppProperties properties = new AppProperties();
        properties.getChatMq().setEnabled(true);
        properties.getChatMq().setRoutingKey("chat.message.sent");
        properties.getChatMq().setReadBatchRoutingKey("chat.read.batch");
        properties.getChatMq().setMaxRetry(3);
        return properties;
    }
}

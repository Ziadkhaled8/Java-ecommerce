package com.ecommerce.dreamstore.notification;

import com.ecommerce.dreamstore.config.RabbitMqConfig;
import com.ecommerce.dreamstore.notification.dto.InvoiceNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Independent RabbitMQ consumer for invoice dispatch.
 * Has zero dependency on database entity models or the core order subsystem,
 * allowing it to run independently or be extracted into a standalone service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceEmailConsumer {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMqConfig.INVOICE_EMAIL_QUEUE)
    public void receiveInvoiceNotification(InvoiceNotificationEvent event) {
        log.info("Received invoice notification from RabbitMQ for orderId={}, recipient={}",
                event.orderId(), event.recipientEmail());
        try {
            emailService.sendInvoiceEmail(event);
        } catch (Exception ex) {
            log.error("Failed to process invoice notification for orderId={}: {}", event.orderId(), ex.getMessage());
            // Rethrow so RabbitMQ can retry or route to Dead Letter Queue (DLQ)
            throw ex;
        }
    }
}

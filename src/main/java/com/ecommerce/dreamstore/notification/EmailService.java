package com.ecommerce.dreamstore.notification;

import com.ecommerce.dreamstore.notification.dto.InvoiceItemDto;
import com.ecommerce.dreamstore.notification.dto.InvoiceNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Service responsible for sending simple plain-text email invoices via SMTP.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:no-reply@dreamstore.com}")
    private String fromEmail;

    public void sendInvoiceEmail(InvoiceNotificationEvent event) {
        log.info("Preparing plain text invoice email for orderId={}, recipient={}",
                event.orderId(), event.recipientEmail());

        String body = buildPlainTextInvoice(event);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(event.recipientEmail());
        message.setSubject("Your Invoice for Order #" + event.orderId() + " - Dream Store");
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("Invoice email sent successfully to {} for orderId={}", event.recipientEmail(), event.orderId());
        } catch (Exception ex) {
            log.error("Failed to send invoice email to {} for orderId={}: {}",
                    event.recipientEmail(), event.orderId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private String buildPlainTextInvoice(InvoiceNotificationEvent event) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.ENGLISH);
        String formattedDate = event.paidAt() != null ? dateFormat.format(event.paidAt()) : "N/A";

        sb.append("============================================================\n");
        sb.append("                    DREAM STORE INVOICE                     \n");
        sb.append("============================================================\n\n");
        sb.append("Dear ").append(event.recipientName() != null ? event.recipientName() : "Customer").append(",\n\n");
        sb.append("Thank you for shopping with Dream Store! Your payment has been received successfully.\n\n");
        sb.append("ORDER DETAILS:\n");
        sb.append("------------------------------------------------------------\n");
        sb.append("Order ID       : #").append(event.orderId()).append("\n");
        sb.append("Transaction ID : ").append(event.transactionId() != null ? event.transactionId() : "N/A").append("\n");
        sb.append("Date Paid      : ").append(formattedDate).append("\n");
        sb.append("Payment Status : COMPLETED\n");
        sb.append("Currency       : ").append(event.currency()).append("\n");
        sb.append("------------------------------------------------------------\n\n");

        sb.append("ITEMS PURCHASED:\n");
        sb.append("------------------------------------------------------------\n");
        if (event.items() != null && !event.items().isEmpty()) {
            for (InvoiceItemDto item : event.items()) {
                sb.append(String.format("• %-28s x %-3d @ %s %s  =  %s %s\n",
                        item.productName(),
                        item.quantity(),
                        item.unitPrice(),
                        event.currency(),
                        item.totalPrice(),
                        event.currency()
                ));
            }
        } else {
            sb.append("• Order Items Summary\n");
        }
        sb.append("------------------------------------------------------------\n");
        sb.append(String.format("TOTAL AMOUNT PAID: %s %s\n", event.amount(), event.currency()));
        sb.append("------------------------------------------------------------\n\n");

        if (event.shippingAddress() != null && !event.shippingAddress().isBlank()) {
            sb.append("SHIPPING ADDRESS:\n");
            sb.append(event.shippingAddress()).append("\n\n");
        }

        sb.append("If you have any questions regarding your order, please contact our support team.\n\n");
        sb.append("Best regards,\n");
        sb.append("The Dream Store Team\n");
        sb.append("============================================================\n");

        return sb.toString();
    }
}

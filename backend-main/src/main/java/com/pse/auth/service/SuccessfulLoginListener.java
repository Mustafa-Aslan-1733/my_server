package com.pse.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Provides SuccessfulLoginListener.
 */
@Component
public class SuccessfulLoginListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuccessfulLoginListener.class);

    private final LoginSuccessDelivery delivery;

    /**
     * Creates SuccessfulLoginListener.
     *
     * @param delivery the delivery
     */
    public SuccessfulLoginListener(LoginSuccessDelivery delivery) {
        this.delivery = delivery;
    }

    /**
     * Executes onSuccessfulLogin.
     *
     * @param event the event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSuccessfulLogin(SuccessfulLoginEvent event) {
        try {
            delivery.send(event.email(), event.ipAddress(), event.userAgent());
        } catch (RuntimeException exception) {
            LOGGER.warn("Successful-login notification could not be delivered");
        }
    }
}

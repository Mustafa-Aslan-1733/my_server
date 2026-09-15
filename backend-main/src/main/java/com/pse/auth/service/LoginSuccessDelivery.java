package com.pse.auth.service;

/**
 * Defines LoginSuccessDelivery.
 */
public interface LoginSuccessDelivery {

    /**
     * Simply sends the LoginSuccessDelivery.
     * @param email email
     * @param ipAddress ipAddress
     * @param userAgent userAgent
     */
    void send(String email, String ipAddress, String userAgent);
}

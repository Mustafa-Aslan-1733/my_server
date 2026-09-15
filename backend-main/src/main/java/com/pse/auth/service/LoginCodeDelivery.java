package com.pse.auth.service;

/**
 * Defines LoginCodeDelivery.
 */
public interface LoginCodeDelivery {

    void send(String email, String code);
}

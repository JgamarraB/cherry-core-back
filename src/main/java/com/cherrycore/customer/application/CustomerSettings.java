package com.cherrycore.customer.application;

/**
 * @param termsVersion versión vigente de los términos y condiciones
 * @param maxAddresses máximo de direcciones por cliente
 */
public record CustomerSettings(String termsVersion, int maxAddresses) {
}

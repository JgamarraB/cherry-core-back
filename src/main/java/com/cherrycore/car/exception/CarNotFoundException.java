package com.cherrycore.car.exception;

public class CarNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final String MESSAGE_TEMPLATE =
            "Car not found with id: %d";

    public CarNotFoundException(Long carId) {
        super(MESSAGE_TEMPLATE.formatted(carId));
    }
}
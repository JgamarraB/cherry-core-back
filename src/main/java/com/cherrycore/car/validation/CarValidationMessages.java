package com.cherrycore.car.validation;

public final class CarValidationMessages {

	public static final String TITLE_REQUIRED =
            "Title is required";

    public static final String TITLE_MAX_LENGTH =
            "Title must not exceed 150 characters";

    public static final String PRICE_REQUIRED =
            "Price is required";

    public static final String PRICE_MIN_VALUE =
            "Price must be greater than or equal to 0.00";

    private CarValidationMessages() {
        throw new IllegalStateException(
                "CarValidationMessages is a constants class and cannot be instantiated"
        );
    }
    
}

package com.grun.calorietracker.exception;

public class AiPhotoInputException extends RuntimeException {
    private final String code;

    public AiPhotoInputException(String code) {
        super(code);
        if (!"NO_FOOD_DETECTED".equals(code) && !"IMAGE_UNCLEAR".equals(code)) {
            throw new IllegalArgumentException("Unsupported photo outcome");
        }
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public String userMessage(boolean turkish) {
        if ("NO_FOOD_DETECTED".equals(code)) {
            return turkish ? "Foto\u011frafta yiyecek veya i\u00e7ecek alg\u0131lanamad\u0131. L\u00fctfen \u00f6\u011f\u00fcn\u00fcn\u00fcz\u00fcn foto\u011fraf\u0131n\u0131 \u00e7ekin."
                    : "No food or drink was detected. Please take a photo of your meal.";
        }
        return turkish ? "G\u00f6r\u00fcnt\u00fcdeki yiyecekler ay\u0131rt edilemiyor. L\u00fctfen daha net ve iyi ayd\u0131nlat\u0131lm\u0131\u015f bir foto\u011fraf \u00e7ekin."
                : "The food in this image cannot be identified. Please take a clearer, well-lit photo.";
    }
}

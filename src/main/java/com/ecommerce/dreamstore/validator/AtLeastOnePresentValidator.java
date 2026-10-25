package com.ecommerce.dreamstore.validator;

import com.ecommerce.dreamstore.annotation.AtLeastOnePresent;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AtLeastOnePresentValidator implements ConstraintValidator<AtLeastOnePresent, Object> {
    private String[] fields;

    @Override
    public void initialize(AtLeastOnePresent constraintAnnotation) {
        this.fields = constraintAnnotation.fields();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }

        for (String field : fields) {
            Object fieldValue = getFieldValue(value, field);
            // Check if the field is present and not an empty/blank string
            if (fieldValue != null && !fieldValue.toString().trim().isEmpty()) {
                return true; // At least one valid field found!
            }
        }
        return false; // None of the fields were provided
    }

    // Helper to extract field values from a record using reflection
    private Object getFieldValue(Object object, String fieldName) {
        try {
            var field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(object);
        } catch (Exception e) {
            return null;
        }
    }
}

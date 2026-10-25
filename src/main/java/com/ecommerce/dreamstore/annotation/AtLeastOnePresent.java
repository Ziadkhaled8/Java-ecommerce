package com.ecommerce.dreamstore.annotation;

import com.ecommerce.dreamstore.validator.AtLeastOnePresentValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = AtLeastOnePresentValidator.class)
@Target({ElementType.TYPE}) // Applied at the class/record level
@Retention(RetentionPolicy.RUNTIME)
public @interface AtLeastOnePresent {
    String message();
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    // The fields to check
    String[] fields();
}

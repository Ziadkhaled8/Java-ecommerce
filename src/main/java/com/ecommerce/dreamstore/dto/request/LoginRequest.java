package com.ecommerce.dreamstore.dto.request;
import com.ecommerce.dreamstore.annotation.AtLeastOnePresent;
import jakarta.validation.constraints.NotBlank;

@AtLeastOnePresent(fields = {"email", "phone"}, message = "You must provide either an email or a phone number.")
public record LoginRequest(
        String email,
        String phone,
        @NotBlank(message = "Password is required")
        String password
) {
}

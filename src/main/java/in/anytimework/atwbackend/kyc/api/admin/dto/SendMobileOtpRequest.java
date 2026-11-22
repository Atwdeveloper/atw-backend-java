package in.anytimework.atwbackend.kyc.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMobileOtpRequest(
        @Schema(description = "Mobile number to send the one-time SMS code to. No format validation beyond the "
                + "length cap - any digits/punctuation up to 20 characters is accepted as-is.",
                example = "9876543210", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 20) String mobileNumber) {
}

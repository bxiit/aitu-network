package aitu.network.aitunetwork.model.dto.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Keys for an account that has none yet (accounts created before end-to-end encryption). */
public record KeyPairSetupRequest(
        @NotBlank(message = "Public key is required")
        String publicKey,

        @NotNull(message = "Encrypted private key is required")
        @Valid
        EncryptedPrivateKeyDto encryptedPrivateKeyDto
) {
}

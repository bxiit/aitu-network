package aitu.network.aitunetwork.controller;

import aitu.network.aitunetwork.common.annotations.CurrentUser;
import aitu.network.aitunetwork.config.security.CustomUserDetails;
import aitu.network.aitunetwork.model.dto.JwtResponse;
import aitu.network.aitunetwork.model.dto.LoginRequest;
import aitu.network.aitunetwork.model.dto.RegisterRequest;
import aitu.network.aitunetwork.model.dto.UserDTO;
import aitu.network.aitunetwork.model.dto.chat.EncryptedPrivateKeyDto;
import aitu.network.aitunetwork.model.dto.chat.KeyPairSetupRequest;
import aitu.network.aitunetwork.model.entity.User;
import aitu.network.aitunetwork.model.mapper.UserMapper;
import aitu.network.aitunetwork.service.AuthService;
import aitu.network.aitunetwork.util.CookieUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    CompletableFuture<User> register(@RequestBody @Valid RegisterRequest request) {
        return authService.registerUser(request);
    }

    @PostMapping("/login")
    JwtResponse login(@RequestBody @Valid LoginRequest loginRequest,
                      HttpServletResponse response) {
        JwtResponse jwtResponse = authService.login(loginRequest);
        CookieUtils.addJwtToCookie(response, jwtResponse.token());
        return jwtResponse;
    }

    @PatchMapping("/resend")
    void resendConfirmationToken(@RequestParam String email) {
        authService.resendConfirmationToken(email);
    }

    @GetMapping("/confirm")
    void confirmAccount(@RequestParam String token) {
        authService.confirmAccount(token);
    }

    @GetMapping("/forgot")
    void forgotPassword(@RequestParam String email) {
        authService.forgotPassword(email);
    }

    @PatchMapping("/recover")
    void recoverPassword(@RequestParam String token,
                         @RequestParam String password) {
        authService.recoverPassword(token, password);
    }

    @GetMapping("/encrypted-private-key")
    ResponseEntity<EncryptedPrivateKeyDto> getEncryptedPrivateKey(@CurrentUser CustomUserDetails userDetails) {
        return ResponseEntity.ok(authService.getEncryptedPrivateKey(userDetails.getUsername()));
    }

    // Accounts created before end-to-end encryption have no key pair; the client generates one at login
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/keys")
    void setUpKeys(@RequestBody @Valid KeyPairSetupRequest request, @CurrentUser CustomUserDetails userDetails) {
        authService.setUpKeys(userDetails.user().getId(), request);
    }

    @GetMapping("/me")
    UserDTO me(@CurrentUser UserDetails userDetails) {
        User user = ((CustomUserDetails) userDetails).user();
        return UserMapper.toDto(user);
    }
}

package aitu.network.aitunetwork.service;

import aitu.network.aitunetwork.common.exception.*;
import aitu.network.aitunetwork.config.security.CustomUserDetails;
import aitu.network.aitunetwork.config.security.CustomUserDetailsService;
import aitu.network.aitunetwork.config.security.JwtService;
import aitu.network.aitunetwork.model.dto.JwtResponse;
import aitu.network.aitunetwork.model.dto.LoginRequest;
import aitu.network.aitunetwork.model.dto.RegisterRequest;
import aitu.network.aitunetwork.model.dto.TokenHolder;
import aitu.network.aitunetwork.model.dto.chat.EncryptedPrivateKey;
import aitu.network.aitunetwork.model.dto.chat.EncryptedPrivateKeyDto;
import aitu.network.aitunetwork.model.dto.chat.KeyPairSetupRequest;
import aitu.network.aitunetwork.model.entity.User;
import aitu.network.aitunetwork.model.enums.AccessType;
import aitu.network.aitunetwork.model.enums.Role;
import aitu.network.aitunetwork.repository.UserRepository;
import aitu.network.aitunetwork.util.EncryptionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;
    private final Executor appExecutor;
    private final MailService mailService;
    private final MongoTemplate mongoTemplate;

    @Value("${secure-talk.mail-confirmation}")
    private Boolean isEmailConfirmationEnabled;

    public CompletableFuture<User> registerUser(RegisterRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                User user = userRepository.save(mapUserDTOToUser(request));
                if (isEmailConfirmationEnabled) {
                    sendVerificationMessage(user);
                }
                return user;
            } catch (DuplicateKeyException e) {
                throw new ConflictException("errors.409.users.email");
            }
        }, appExecutor).exceptionally(e -> {
            throw new RuntimeException("Error while register of user: " + e.getMessage(), e);
        });
    }

    public JwtResponse login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.email(),
                request.password()
        ));
        var user = (CustomUserDetails) customUserDetailsService.
                loadUserByUsername(request.email());
        if (!user.user().isEnabled()) {
            throw new UnauthorizedException("Account is not confirmed, check your mail!");
        }
        var jwt = jwtService.generateToken(user);
        log.info("User {} logged in", request.email());
        return new JwtResponse(jwt);
    }

    public EncryptedPrivateKeyDto getEncryptedPrivateKey(String userEmail) {
        User user = userRepository.findByIdOrEmail(userEmail)
                .orElseThrow(() -> new NotFoundException(User.class, "email", userEmail));

        EncryptedPrivateKey encryptedKey = user.getEncryptedPrivateKey();
        if (encryptedKey == null) {
            throw new NotFoundException("Encrypted private key not found for user");
        }

        return new EncryptedPrivateKeyDto(
                encryptedKey.getEncryptedKey(),
                encryptedKey.getSalt(),
                encryptedKey.getIv()
        );
    }

    public void setUpKeys(String userId, KeyPairSetupRequest request) {
        if (!EncryptionUtils.validatePublicKey(request.publicKey())) {
            throw new BadRequestException("Invalid public key format");
        }
        EncryptedPrivateKeyDto dto = request.encryptedPrivateKeyDto();
        EncryptedPrivateKey encryptedPrivateKey = EncryptedPrivateKey.builder()
                .encryptedKey(dto.encryptedKey())
                .salt(dto.salt())
                .iv(dto.iv())
                .build();
        // Only while the account has no keys, checked atomically: replacing an existing public key would let
        // whoever holds the session read the account's future messages.
        Query noKeysYet = new Query(Criteria.where("_id").is(userId)
                .and(User.Fields.publicKey).is(null)
                .and(User.Fields.encryptedPrivateKey).is(null));
        Update setKeys = new Update()
                .set(User.Fields.publicKey, request.publicKey())
                .set(User.Fields.encryptedPrivateKey, encryptedPrivateKey);
        if (mongoTemplate.updateFirst(noKeysYet, setKeys, User.class).getModifiedCount() == 0) {
            throw new ConflictException("errors.409.users.keys");
        }
    }

    // TODO реализовать функционал для этого. Пока не использовать. Изменение пароля сломает чат
    //  если зайти потом с другого браузера или устройства
    public void updateEncryptedPrivateKey(String userEmail, EncryptedPrivateKeyDto newEncryptedKey) {
        User user = userRepository.findByIdOrEmail(userEmail)
                .orElseThrow(() -> new NotFoundException(User.class, "email", userEmail));

        EncryptedPrivateKey encryptedPrivateKey = EncryptedPrivateKey.builder()
                .encryptedKey(newEncryptedKey.encryptedKey())
                .salt(newEncryptedKey.salt())
                .iv(newEncryptedKey.iv())
                .build();

        user.setEncryptedPrivateKey(encryptedPrivateKey);
        userRepository.save(user);
    }

    public void confirmAccount(String token) {
        Query query = new Query(Criteria.where("verificationTokenHolder.token").is(token));
        User user = Optional.ofNullable(mongoTemplate.findOne(query, User.class)).orElseThrow(() ->
                new NotFoundException(String.format("%s user with this verification token not found",
                        token)));
        if (user.isEnabled()) {
            throw new GoneException("User is already enabled");
        }
        validateTokenHolder(user.getVerificationTokenHolder(), token);
        user.setEnabled(true);
        userRepository.save(user);
    }

    public void resendConfirmationToken(String username) {
        User user = userRepository.findByIdOrEmail(username)
                .orElseThrow(() ->
                        new NotFoundException(User.class, User.Fields.email, username));
        var verificationTokenHolder = new TokenHolder(UUID.randomUUID().toString(),
                LocalDateTime.now().plusHours(24));
        user.setVerificationTokenHolder(verificationTokenHolder);
        userRepository.save(user);
        sendVerificationMessage(user);
    }

    public void forgotPassword(String email) {
        User user = userRepository.findByIdOrEmail(email).orElseThrow(() ->
                new NotFoundException(User.class, "email", email));
        TokenHolder recoverTokenHolder = new TokenHolder(UUID.randomUUID().toString(),
                LocalDateTime.now().plusHours(24));
        user.setRecoverTokenHolder(recoverTokenHolder);
        userRepository.save(user);
        CompletableFuture.runAsync(() -> mailService.sendForgotPasswordMessage(user), appExecutor);
    }

    public void recoverPassword(String token, String password) {
        Query query = new Query(Criteria.where("recoverTokenHolder.token").is(token));
        User user = Optional.ofNullable(mongoTemplate.findOne(query, User.class))
                .orElseThrow(() ->
                        new NotFoundException(String.format("%s user with this verification token not found",
                                token)));
        validateTokenHolder(user.getRecoverTokenHolder(), token);
        user.setPassword(passwordEncoder.encode(password));
        user.setRecoverTokenHolder(null);
        userRepository.save(user);
    }

    private void validateTokenHolder(TokenHolder tokenHolder, String token) {
        if (Objects.isNull(tokenHolder)) {
            throw new BadRequestException("Token holder is missing");
        }
        if (Objects.isNull(tokenHolder.token())) {
            throw new BadRequestException("Recover token is not assigned to the user");
        }
        if (!Objects.equals(token, tokenHolder.token())) {
            throw new BadRequestException("Provided token does not match the stored token");
        }
        if (Objects.isNull(tokenHolder.expiryDate())) {
            throw new ConflictException("Recover token has no expiration date");
        }
        if (LocalDateTime.now().isAfter(tokenHolder.expiryDate())) {
            throw new BadRequestException("Recover token has expired");
        }
    }

    private User mapUserDTOToUser(RegisterRequest userDTO) {
        if (!EncryptionUtils.validatePublicKey(userDTO.publicKey())) {
            throw new BadRequestException("Invalid public key format");
        }

        if (userDTO.encryptedPrivateKeyDto() == null) {
            throw new BadRequestException("Encrypted private key is required");
        }

        EncryptedPrivateKey encryptedPrivateKey = EncryptedPrivateKey.builder()
                .encryptedKey(userDTO.encryptedPrivateKeyDto().encryptedKey())
                .salt(userDTO.encryptedPrivateKeyDto().salt())
                .iv(userDTO.encryptedPrivateKeyDto().iv())
                .build();

        return User.builder()
                .email(userDTO.email())
                .username(userDTO.username())
                .password(passwordEncoder.encode(userDTO.password()))
                .roles(List.of(Role.USER))
                .enabled(!isEmailConfirmationEnabled)
                .verificationTokenHolder(new TokenHolder(UUID.randomUUID().toString(),
                        LocalDateTime.now().plusHours(24)))
                .friendList(new ArrayList<>())
                .accessType(AccessType.PUBLIC)
                .publicKey(userDTO.publicKey())
                .encryptedPrivateKey(encryptedPrivateKey)
                .build();
    }

    private void sendVerificationMessage(User user) {
        CompletableFuture.runAsync(
                () -> mailService.sendConfirmationMessage(user),
                appExecutor);
    }
}
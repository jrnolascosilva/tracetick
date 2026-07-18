package com.tracetick.auth;

import com.tracetick.domain.PasswordResetToken;
import com.tracetick.domain.User;
import com.tracetick.persistence.PasswordResetTokenRepository;
import com.tracetick.persistence.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenGenerator tokenGenerator;
    private final PasswordResetProperties properties;
    private final Clock clock;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                PasswordResetTokenGenerator tokenGenerator,
                                PasswordResetProperties properties,
                                Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String requestReset(String email) {
        String rawToken = tokenGenerator.generate();
        Optional<User> user = userRepository.findByEmailForUpdate(email)
                .filter(User::isActive);
        if (user.isEmpty()) {
            return rawToken;
        }

        Instant now = clock.instant();
        List<PasswordResetToken> olderTokens =
                tokenRepository.findAllByUserIdAndInvalidatedAtIsNull(user.get().getId());
        olderTokens.forEach(token -> token.invalidate(now));
        tokenRepository.saveAll(olderTokens);

        PasswordResetToken token = PasswordResetToken.issue(
                user.get(), tokenGenerator.hash(rawToken), now,
                now.plus(properties.tokenTtl()));
        tokenRepository.save(token);
        return rawToken;
    }
}

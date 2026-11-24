package com.flagship.server.security;

import com.flagship.server.entity.AppUser;
import com.flagship.server.repository.AppUserRepository;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** On the very first start (no users yet) creates the site admin "admin". */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final System.Logger LOG = System.getLogger(AdminBootstrap.class.getName());

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String configuredPassword;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder encoder,
                          @Value("${flagship.security.admin-password:}") String configuredPassword) {
        this.users = users;
        this.encoder = encoder;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        boolean generated = configuredPassword == null || configuredPassword.isBlank();
        if (!generated && configuredPassword.length() < 8) {
            throw new IllegalStateException("flagship.security.admin-password must have at least 8 characters");
        }
        String password = generated ? randomPassword() : configuredPassword;

        AppUser admin = new AppUser();
        admin.setUsername("admin");
        admin.setPasswordHash(encoder.encode(password));
        admin.setSiteAdmin(true);
        users.save(admin);

        if (generated) {
            LOG.log(System.Logger.Level.WARNING,
                    "FlagShip: created the user ''admin'' with the generated password: {0}  "
                            + "(set FLAGSHIP_SECURITY_ADMIN_PASSWORD before the first start to choose your own)",
                    password);
        } else {
            LOG.log(System.Logger.Level.INFO, "FlagShip: created the user ''admin''");
        }
    }

    private static String randomPassword() {
        String chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
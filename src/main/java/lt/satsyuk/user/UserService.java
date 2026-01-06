package lt.satsyuk.user;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) {
        // In-memory test user. Пароль {noop}password или можно использовать BCrypt.
        return User.withUsername(username)
                //.password("{noop}password")
                .password(new BCryptPasswordEncoder().encode("password"))
                .roles("USER")
                .build();
    }
}

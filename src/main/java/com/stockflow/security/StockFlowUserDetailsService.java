package com.stockflow.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.stockflow.user.UserService;

/** Bridges Spring Security's login to our users. "Username" is the email address. */
@Service
public class StockFlowUserDetailsService implements UserDetailsService {

    private final UserService userService;

    public StockFlowUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        // Spring turns this into a generic "bad credentials" error, so callers cannot tell it apart
        // from a wrong password.
        return userService.findCredentialsByEmail(email)
                .map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account for this email"));
    }
}

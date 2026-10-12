package com.travelops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Users authenticate with JWTs, not Spring's in-memory user with a generated password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class TravelOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelOpsApplication.class, args);
    }
}

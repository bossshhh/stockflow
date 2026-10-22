package com.stockflow.config;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;

/** Opening http://localhost:8080 sends people to the API docs instead of a confusing 401. */
@Hidden
@RestController
public class RootController {

    private static final URI SWAGGER_UI = URI.create("/swagger-ui.html");

    @GetMapping("/")
    public ResponseEntity<Void> root() {
        return ResponseEntity.status(HttpStatus.FOUND).location(SWAGGER_UI).build();
    }
}

package nz.ac.aut.comp713.login_service.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nz.ac.aut.comp713.login_service.dto.LoginRequest;
import nz.ac.aut.comp713.login_service.dto.RegisterRequest;
import nz.ac.aut.comp713.login_service.dto.SubmissionResponse;
import nz.ac.aut.comp713.login_service.service.AuthSubmissionService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthApiController {

    private final AuthSubmissionService submissionService;

    public AuthApiController(AuthSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping("/login")
    public ResponseEntity<SubmissionResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.accepted().body(submissionService.receiveLogin(request));
    }

    @PostMapping("/register")
    public ResponseEntity<SubmissionResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.accepted().body(submissionService.receiveRegistration(request));
    }
}

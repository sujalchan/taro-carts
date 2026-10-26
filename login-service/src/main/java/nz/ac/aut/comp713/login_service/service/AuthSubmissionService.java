package nz.ac.aut.comp713.login_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import nz.ac.aut.comp713.login_service.dto.LoginRequest;
import nz.ac.aut.comp713.login_service.dto.RegisterRequest;
import nz.ac.aut.comp713.login_service.dto.SubmissionResponse;

@Service
public class AuthSubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(AuthSubmissionService.class);

    public SubmissionResponse receiveLogin(LoginRequest request) {
        logger.info("Login form information was successfully received");
        return new SubmissionResponse("Login form information was successfully received. Authentication is not enabled yet.");
    }

    public SubmissionResponse receiveRegistration(RegisterRequest request) {
        logger.info("Registration form information was successfully received");
        return new SubmissionResponse("Registration form information was successfully received. Account creation is not enabled yet.");
    }
}

package nz.ac.aut.comp713.customer_service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import nz.ac.aut.comp713.customer_service.service.GoogleAddressValidationService;

@TestConfiguration
class TestAddressValidationConfiguration {
    @Bean
    @Primary
    GoogleAddressValidationService testAddressValidationService() {
        return new GoogleAddressValidationService("") {
            @Override
            public String validate(String submittedAddress) {
                return submittedAddress;
            }
        };
    }
}

package nz.ac.aut.comp713.login_service.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nz.ac.aut.comp713.login_service.service.ReverseGeocodingService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MobileAddressControllerTest {
    @Test
    void returnsTheServerResolvedAddress() throws Exception {
        var geocoding = mock(ReverseGeocodingService.class);
        when(geocoding.reverse(-36.8485, 174.7688))
                .thenReturn("1 Queen Street, Auckland, New Zealand");
        var mvc = MockMvcBuilders.standaloneSetup(new MobileAddressController(geocoding)).build();

        mvc.perform(post("/api/v1/mobile/address/reverse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":-36.8485,\"longitude\":174.7688}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("1 Queen Street, Auckland, New Zealand"));
        verify(geocoding).reverse(-36.8485, 174.7688);
    }
}

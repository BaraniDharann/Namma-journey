package com.travelplatform.service;

import com.travelplatform.config.ExternalHttpClients;
import com.travelplatform.config.RazorpayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only class that talks to Razorpay's servers. Kept to the one call the flow needs - creating
 * an order - so the rest of the integration is plain, testable code around signatures.
 *
 * <p>Plain REST rather than Razorpay's Java SDK: one endpoint does not justify a dependency, and
 * this keeps the bounded timeouts every other third-party call here uses.
 */
@Component
public class RazorpayGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);

    private final RazorpayProperties properties;
    private final RestTemplate http = ExternalHttpClients.forThirdPartyApis();

    public RazorpayGateway(RazorpayProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates an order and returns its id ({@code order_...}).
     *
     * @param amountPaise what the customer will be charged, in paise - Razorpay takes the
     *                    smallest currency unit, so 2500.00 rupees is 250000
     * @param receipt     our reference, at most 40 characters
     */
    public String createOrder(long amountPaise, String receipt, Map<String, String> notes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(properties.getKeyId(), properties.getKeySecret());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", "INR");
        body.put("receipt", receipt);
        body.put("notes", notes);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> order = http.postForObject(
                    properties.getApiBaseUrl() + "/v1/orders", new HttpEntity<>(body, headers), Map.class);
            Object id = order == null ? null : order.get("id");
            if (id == null) {
                throw new IllegalStateException("Razorpay returned an order without an id");
            }
            return id.toString();
        } catch (RestClientException | IllegalStateException e) {
            // The detail (status, Razorpay's error body) stays in the log; the passenger gets a
            // message they can act on.
            log.error("Razorpay order creation failed for receipt {}", receipt, e);
            throw new RuntimeException("Online payment is unavailable right now. Please try again, or pay by UPI or cash.");
        }
    }
}

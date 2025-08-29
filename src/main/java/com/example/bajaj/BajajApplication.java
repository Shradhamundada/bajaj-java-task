package com.example.bajaj;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class BajajApplication implements CommandLineRunner {

    private final RestTemplate restTemplate = new RestTemplate();

    public static void main(String[] args) {
        SpringApplication.run(BajajApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // === 1) Generate webhook per spec ===
        // Doc: POST https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA
        // Body: { "name": "...", "regNo": "...", "email": "..." }  (field names exactly)  [Spec PDF]
        final String generateUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

        // TODO: fill your real details
        Map<String, Object> genBody = new HashMap<>();
        genBody.put("name",  "Shradha Mundada");
        genBody.put("regNo", "22BIT0112"); // <-- even last two digits → Question 2 per PDF
        genBody.put("email", "shradha.mundada2022@vitstudent.ac.in");

        HttpHeaders genHeaders = new HttpHeaders();
        genHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> genReq = new HttpEntity<>(genBody, genHeaders);

        ResponseEntity<Map<String, Object>> genResp = restTemplate.exchange(
                generateUrl,
                HttpMethod.POST,
                genReq,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        Map<String, Object> respBody = genResp.getBody();
        if (respBody == null || !respBody.containsKey("webhook") || !respBody.containsKey("accessToken")) {
            throw new IllegalStateException("Unexpected response from generateWebhook: " + genResp);
        }

        final String webhookUrl  = String.valueOf(respBody.get("webhook"));
        final String accessToken = String.valueOf(respBody.get("accessToken"));

        System.out.println("✅ generateWebhook response:");
        System.out.println("   webhook     = " + webhookUrl);
        System.out.println("   accessToken = " + accessToken);

        // === 2) Build final SQL query exactly per Question 2 ===
        // Problem: for each employee, count number of employees in SAME department who are YOUNGER (DOB greater means younger),
        // output EMP_ID, FIRST_NAME, LAST_NAME, DEPARTMENT_NAME, YOUNGER_EMPLOYEES_COUNT,
        // ordered by EMP_ID DESC.  [Question 2 PDF]
        final String finalQuery =
                "SELECT " +
                "  e1.EMP_ID, " +
                "  e1.FIRST_NAME, " +
                "  e1.LAST_NAME, " +
                "  d.DEPARTMENT_NAME, " +
                "  COUNT(e2.EMP_ID) AS YOUNGER_EMPLOYEES_COUNT " +
                "FROM EMPLOYEE e1 " +
                "JOIN DEPARTMENT d " +
                "  ON e1.DEPARTMENT = d.DEPARTMENT_ID " +
                "LEFT JOIN EMPLOYEE e2 " +
                "  ON e1.DEPARTMENT = e2.DEPARTMENT " +
                " AND e2.DOB > e1.DOB " +    // younger = later DOB
                "GROUP BY e1.EMP_ID, e1.FIRST_NAME, e1.LAST_NAME, d.DEPARTMENT_NAME " +
                "ORDER BY e1.EMP_ID DESC;";

        // === 3) Submit final query per spec ===
        // Doc says: POST https://bfhldevapigw.healthrx.co.in/hiring/testWebhook/JAVA
        // BUT step 2 also says you'll receive a webhook URL to submit your answer.
        // We strictly use the returned `webhook` URL and set header:
        //   Authorization: <accessToken>  (NO 'Bearer ' prefix per spec)  [Spec PDF]
        Map<String, String> submitBody = new HashMap<>();
        submitBody.put("finalQuery", finalQuery); // field name exactly as required

        HttpHeaders submitHeaders = new HttpHeaders();
        submitHeaders.setContentType(MediaType.APPLICATION_JSON);
        submitHeaders.add("Authorization", accessToken); // << NO "Bearer " prefix (matches PDF)

        HttpEntity<Map<String, String>> submitReq = new HttpEntity<>(submitBody, submitHeaders);

        System.out.println("➡️  Submitting to webhook: " + webhookUrl);
        System.out.println("➡️  Authorization (raw token): " + accessToken);
        System.out.println("➡️  Payload: " + submitBody);

        try {
            ResponseEntity<String> submitResp = restTemplate.postForEntity(webhookUrl, submitReq, String.class);
            System.out.println("✅ Final submission response: " + submitResp.getStatusCodeValue() + " " + submitResp.getBody());
        } catch (HttpClientErrorException e) {
            System.err.println("❌ Submission failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
            System.err.println("Hint: If you previously sent 'Bearer <token>', remove 'Bearer ' (spec wants raw token).");
            throw e;
        }
    }
}

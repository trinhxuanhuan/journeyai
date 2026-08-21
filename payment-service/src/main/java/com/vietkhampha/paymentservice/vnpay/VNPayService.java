package com.vietkhampha.paymentservice.vnpay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Locale;
import java.util.TreeMap;

@Component
public class VNPayService {

    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;

    public VNPayService(
            @Value("${app.vnpay.tmn-code}") String tmnCode,
            @Value("${app.vnpay.hash-secret}") String hashSecret,
            @Value("${app.vnpay.pay-url}") String payUrl,
            @Value("${app.vnpay.return-url}") String returnUrl
    ) {
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.payUrl = payUrl;
        this.returnUrl = returnUrl;
    }

    public record PaymentUrlResult(String redirectUrl, String transactionRef) {}

    public PaymentUrlResult createPaymentUrl(
            BigDecimal amount,
            String orderInfo,
            String ipAddress,
            Instant expiresAt
    ) {
        String txnRef = generateTransactionRef();
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        ZonedDateTime expiration = expiresAt.atZone(VN_ZONE);

        if (!expiration.isAfter(now)) {
            throw new IllegalArgumentException("VNPay expiration must be in the future");
        }

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", ipAddress);
        params.put("vnp_CreateDate", now.format(VNPAY_DATE_FORMAT));
        params.put("vnp_ExpireDate", expiration.format(VNPAY_DATE_FORMAT));

        String queryString = buildQueryString(params);
        String secureHash = hmacSHA512(hashSecret, queryString);

        String redirectUrl = payUrl + "?" + queryString + "&vnp_SecureHash=" + secureHash;
        return new PaymentUrlResult(redirectUrl, txnRef);
    }

    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }
        return sb.toString();
    }

    private String generateTransactionRef() {

        SecureRandom random = new SecureRandom();
        return System.currentTimeMillis() + "" + (random.nextInt(900000) + 100000);
    }

    public String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Loi tinh HMAC-SHA512", e);
        }
    }
    public boolean verifyChecksum(Map<String, String> allParams) {
        String receivedHash = allParams.get("vnp_SecureHash");
        if (receivedHash == null || !receivedHash.matches("(?i)[0-9a-f]{128}")) {
            return false;
        }

        Map<String, String> paramsToVerify = new TreeMap<>();
        allParams.forEach((key, value) -> {
            if (key.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key)) {
                paramsToVerify.put(key, value);
            }
        });

        String queryString = buildQueryString(paramsToVerify);
        String calculatedHash = hmacSHA512(hashSecret, queryString);

        return MessageDigest.isEqual(
                calculatedHash.getBytes(StandardCharsets.US_ASCII),
                receivedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)
        );
    }

    public boolean isExpectedTmnCode(String receivedTmnCode) {
        return tmnCode.equals(receivedTmnCode);
    }
    public record RefundResult(boolean success, String message, String gatewayRefundRef) {}
    public RefundResult createRefundRequest(String originalTxnRef, java.math.BigDecimal amount,
                                            String originalTransactionDate, String ipAddress) {
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        String requestId = String.valueOf(System.currentTimeMillis());
        String createDate = now.format(VNPAY_DATE_FORMAT);
        String amountStr = amount.multiply(java.math.BigDecimal.valueOf(100)).toBigInteger().toString();

        String hashRawData = String.join("|",
                requestId, "2.1.0", "refund", tmnCode, "02", originalTxnRef,
                amountStr, "", originalTransactionDate, "system", createDate, ipAddress,
                "Hoan tien booking " + originalTxnRef
        );
        String secureHash = hmacSHA512(hashSecret, hashRawData);

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", "2.1.0");
        body.put("vnp_Command", "refund");
        body.put("vnp_TmnCode", tmnCode);
        body.put("vnp_TransactionType", "02");
        body.put("vnp_TxnRef", originalTxnRef);
        body.put("vnp_Amount", amountStr);
        body.put("vnp_TransactionDate", originalTransactionDate);
        body.put("vnp_CreateBy", "system");
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", ipAddress);
        body.put("vnp_OrderInfo", "Hoan tien booking " + originalTxnRef);
        body.put("vnp_SecureHash", secureHash);

        try {
            org.springframework.web.client.RestClient restClient = org.springframework.web.client.RestClient.create();
            Map<?, ?> response = restClient.post()
                    .uri("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String responseCode = String.valueOf(response.get("vnp_ResponseCode"));
            boolean success = "00".equals(responseCode);
            String refundRef = success ? String.valueOf(response.get("vnp_TransactionNo")) : null;

            return new RefundResult(success, String.valueOf(response.get("vnp_Message")), refundRef);
        } catch (Exception e) {
            return new RefundResult(false, "Loi goi VNPay refund API: " + e.getMessage(), null);
        }
    }
}

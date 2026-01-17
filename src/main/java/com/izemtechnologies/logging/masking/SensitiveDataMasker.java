package com.izemtechnologies.logging.masking;

import com.izemtechnologies.logging.properties.LoggingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprehensive service for masking sensitive data in compliance with RGPD/GDPR.
 * 
 * <p>This component provides multiple masking strategies to protect sensitive
 * information while maintaining log usefulness for debugging and analysis.</p>
 * 
 * <h2>Supported Categories:</h2>
 * <ul>
 *   <li><strong>Personal Data (RGPD Art. 4):</strong> Names, emails, phones, addresses, DOB</li>
 *   <li><strong>Government IDs:</strong> SSN, NIR, passports, driver's licenses</li>
 *   <li><strong>Financial (PCI-DSS):</strong> Credit cards, IBAN, bank accounts</li>
 *   <li><strong>Secrets and Credentials:</strong> Passwords, tokens, API keys</li>
 *   <li><strong>Cloud Secrets:</strong> AWS, Azure, GCP credentials</li>
 *   <li><strong>Vault Secrets:</strong> HashiCorp Vault, K8s secrets</li>
 *   <li><strong>Health Data (RGPD Special):</strong> Medical records, insurance IDs</li>
 * </ul>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensitiveDataMasker {

    private final LoggingProperties properties;

    private final Map<String, Pattern> customPatternCache = new ConcurrentHashMap<>();
    private final AtomicLong tokenCounter = new AtomicLong(0);
    private final Map<String, String> tokenStore = new ConcurrentHashMap<>();
    private final Map<String, String> reverseTokenStore = new ConcurrentHashMap<>();

    /**
     * Default sensitive keywords for automatic field detection.
     */
    private static final Set<String> DEFAULT_SENSITIVE_KEYWORDS = Set.of(
        // Personal data
        "email", "mail", "phone", "mobile", "tel", "fax", "address", "addr",
        "name", "firstname", "lastname", "surname", "fullname", "dob", "birth",
        "ssn", "nir", "passport", "license", "licence", "national_id", "identity",
        
        // Financial
        "card", "credit", "debit", "cvv", "cvc", "iban", "bic", "swift", "account",
        "bank", "routing", "rib",
        
        // Authentication & Secrets
        "password", "passwd", "pwd", "pass", "secret", "credential", "auth",
        "token", "jwt", "bearer", "apikey", "api_key", "api-key", "access_key",
        "private_key", "privatekey", "encryption", "decrypt", "encrypt",
        
        // Cloud & Vault
        "aws_secret", "aws_access", "azure_key", "azure_secret", "gcp_key",
        "vault_token", "vault_secret", "connection_string", "connectionstring",
        
        // Session & OAuth
        "session", "sessionid", "session_id", "oauth", "refresh_token", "access_token",
        
        // Database
        "db_password", "database_url", "jdbc", "connection",
        
        // French specific
        "mot_de_passe", "numero_secu", "carte_bancaire", "compte_bancaire"
    );

    /**
     * Masks all sensitive data in the input string using all enabled patterns.
     * 
     * @param input the input string to mask
     * @return the masked string
     */
    public String mask(String input) {
        if (!isEnabled() || !StringUtils.hasText(input)) {
            return input;
        }

        String result = input;

        // Apply patterns based on enabled categories
        LoggingProperties.MaskingProperties maskingProps = properties.getMasking();

        // Personal Data (RGPD)
        if (maskingProps.isMaskPersonalData()) {
            result = maskCategory(result, SensitiveType::isPersonalData);
        }

        // Financial Data (PCI-DSS)
        if (maskingProps.isMaskFinancialData()) {
            result = maskCategory(result, SensitiveType::isFinancialData);
        }

        // Secrets & Credentials
        if (maskingProps.isMaskSecrets()) {
            result = maskCategory(result, SensitiveType::isSecret);
        }

        // Health Data (RGPD Special Category)
        if (maskingProps.isMaskHealthData()) {
            result = maskCategory(result, SensitiveType::isHealthData);
        }

        // Apply specific enabled types
        for (SensitiveType type : maskingProps.getEnabledTypes()) {
            if (type.hasPattern()) {
                result = maskPattern(result, type.getPattern(), type, getDefaultMode());
            }
        }

        // Apply custom patterns from configuration
        for (var entry : maskingProps.getCustomPatterns().entrySet()) {
            Pattern pattern = getOrCompilePattern(entry.getKey(), entry.getValue());
            if (pattern != null) {
                result = maskPattern(result, pattern, SensitiveType.CUSTOM, getDefaultMode());
            }
        }

        // Apply field-specific patterns (JSON fields)
        for (String fieldPattern : maskingProps.getMaskedFields()) {
            result = maskJsonField(result, fieldPattern);
        }

        return result;
    }

    /**
     * Masks a specific value according to the given type and mode.
     * 
     * @param value the value to mask
     * @param type  the type of sensitive data
     * @param mode  the masking mode
     * @return the masked value
     */
    public String maskValue(String value, SensitiveType type, MaskingMode mode) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        return switch (mode) {
            case FULL -> maskFull(type);
            case PARTIAL -> maskPartial(value, type);
            case HASH -> maskHash(value);
            case TOKENIZE -> maskTokenize(value);
            case REDACT -> "[REDACTED]";
            case FORMAT_PRESERVING -> maskFormatPreserving(value);
        };
    }

    /**
     * Masks a value using the default mode for the given type.
     * 
     * @param value the value to mask
     * @param type  the type of sensitive data
     * @return the masked value
     */
    public String maskValue(String value, SensitiveType type) {
        return maskValue(value, type, getDefaultMode());
    }

    /**
     * Masks a map of values, applying masking to values whose keys match sensitive patterns.
     * 
     * @param data the map to mask
     * @return a new map with masked values
     */
    public Map<String, Object> maskMap(Map<String, Object> data) {
        if (data == null || !isEnabled()) {
            return data;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSensitiveKey(key)) {
                SensitiveType detectedType = detectTypeFromKey(key);
                result.put(key, maskValue(String.valueOf(value), detectedType, getDefaultMode()));
            } else if (value instanceof String s) {
                result.put(key, mask(s));
            } else if (value instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) m;
                result.put(key, maskMap(nested));
            } else if (value instanceof Collection<?> c) {
                result.put(key, maskCollection(c));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * Masks sensitive data in a collection.
     * 
     * @param collection the collection to mask
     * @return a new collection with masked values
     */
    @SuppressWarnings("unchecked")
    public Collection<?> maskCollection(Collection<?> collection) {
        if (collection == null || !isEnabled()) {
            return collection;
        }

        List<Object> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof String s) {
                result.add(mask(s));
            } else if (item instanceof Map<?, ?> m) {
                result.add(maskMap((Map<String, Object>) m));
            } else if (item instanceof Collection<?> c) {
                result.add(maskCollection(c));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Reverses tokenization for a given token.
     * Only works for values masked with TOKENIZE mode.
     * 
     * @param token the token to reverse
     * @return the original value, or null if not found
     */
    public String reverseToken(String token) {
        return tokenStore.get(token);
    }

    /**
     * Gets the token for a given original value (if tokenized).
     * 
     * @param originalValue the original value
     * @return the token, or null if not tokenized
     */
    public String getTokenForValue(String originalValue) {
        return reverseTokenStore.get(originalValue);
    }

    /**
     * Clears the token store (useful for testing or periodic cleanup).
     */
    public void clearTokenStore() {
        tokenStore.clear();
        reverseTokenStore.clear();
        tokenCounter.set(0);
    }

    // ==================== Private Methods ====================

    private String maskCategory(String input, java.util.function.Predicate<SensitiveType> categoryFilter) {
        String result = input;
        for (SensitiveType type : SensitiveType.values()) {
            if (categoryFilter.test(type) && type.hasPattern()) {
                result = maskPattern(result, type.getPattern(), type, getDefaultMode());
            }
        }
        return result;
    }

    private String maskPattern(String input, Pattern pattern, SensitiveType type, MaskingMode mode) {
        try {
            Matcher matcher = pattern.matcher(input);
            StringBuilder result = new StringBuilder();

            while (matcher.find()) {
                String match = matcher.group();
                String masked = maskValue(match, type, mode);
                matcher.appendReplacement(result, Matcher.quoteReplacement(masked));
            }
            matcher.appendTail(result);

            return result.toString();
        } catch (Exception e) {
            log.debug("Error masking pattern {}: {}", type, e.getMessage());
            return input;
        }
    }

    private String maskFull(SensitiveType type) {
        return "***" + type.name() + "***";
    }

    private String maskPartial(String value, SensitiveType type) {
        if (value.length() <= 4) {
            return "****";
        }

        return switch (type) {
            case EMAIL -> maskEmail(value);
            case CREDIT_CARD, CREDIT_CARD_FORMATTED -> maskCreditCard(value);
            case PHONE -> maskPhone(value);
            case SSN, FRENCH_NIR, GERMAN_TAX_ID, UK_NIN -> maskGovernmentId(value);
            case IP_ADDRESS -> maskIpAddress(value);
            case IP_ADDRESS_V6 -> maskIpV6Address(value);
            case JWT, BEARER_TOKEN, OAUTH_TOKEN -> maskToken(value);
            case IBAN, FRENCH_RIB -> maskBankAccount(value);
            case AWS_ACCESS_KEY, AWS_SECRET_KEY, AZURE_STORAGE_KEY, GCP_API_KEY -> maskCloudKey(value);
            case VAULT_TOKEN, GITHUB_TOKEN, GITLAB_TOKEN, SLACK_TOKEN -> maskServiceToken(value);
            case PRIVATE_KEY -> maskPrivateKey(value);
            case DATABASE_URL -> maskDatabaseUrl(value);
            case FULL_NAME -> maskName(value);
            case ADDRESS -> maskAddress(value);
            case PASSWORD -> "********";
            default -> maskGeneric(value);
        };
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***@***";
        }

        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        int dotIndex = domain.lastIndexOf('.');

        String maskedLocal = local.length() > 1 
            ? local.charAt(0) + "***" 
            : "***";
        String maskedDomain = dotIndex > 0 
            ? domain.charAt(0) + "***" + domain.substring(dotIndex)
            : "***";

        return maskedLocal + "@" + maskedDomain;
    }

    private String maskCreditCard(String card) {
        String digits = card.replaceAll("[\\s\\-]", "");
        if (digits.length() < 8) {
            return "****-****-****-****";
        }
        return digits.substring(0, 4) + "-****-****-" + digits.substring(digits.length() - 4);
    }

    private String maskPhone(String phone) {
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.length() < 6) {
            return "****";
        }

        if (digits.startsWith("+")) {
            int prefixLen = Math.min(4, digits.length() - 4);
            return digits.substring(0, prefixLen) + "****" + digits.substring(digits.length() - 2);
        }
        return digits.substring(0, 2) + "****" + digits.substring(digits.length() - 2);
    }

    private String maskGovernmentId(String id) {
        String clean = id.replaceAll("[^0-9A-Za-z]", "");
        if (clean.length() < 4) {
            return "****";
        }
        return "***-**-" + clean.substring(clean.length() - 4);
    }

    private String maskIpAddress(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return "***.***.***.***";
        }
        return parts[0] + ".***.***." + parts[3];
    }

    private String maskIpV6Address(String ip) {
        String[] parts = ip.split(":");
        if (parts.length < 4) {
            return "****:****:****:****";
        }
        return parts[0] + ":****:****:" + parts[parts.length - 1];
    }

    private String maskToken(String token) {
        if (token.length() <= 12) {
            return "****...****";
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
    }

    private String maskBankAccount(String account) {
        String clean = account.replaceAll("[\\s]", "");
        if (clean.length() < 8) {
            return "****";
        }
        return clean.substring(0, 4) + "****" + clean.substring(clean.length() - 4);
    }

    private String maskCloudKey(String key) {
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private String maskServiceToken(String token) {
        if (token.length() <= 10) {
            return "****";
        }
        // Keep prefix (e.g., "ghp_", "glpat-", "xoxb-")
        int prefixEnd = Math.max(token.indexOf('_'), token.indexOf('-'));
        if (prefixEnd > 0 && prefixEnd < 10) {
            return token.substring(0, prefixEnd + 1) + "****";
        }
        return token.substring(0, 6) + "****";
    }

    private String maskPrivateKey(String key) {
        if (key.contains("BEGIN") && key.contains("END")) {
            return "-----BEGIN PRIVATE KEY-----\n[REDACTED]\n-----END PRIVATE KEY-----";
        }
        return "[REDACTED_PRIVATE_KEY]";
    }

    private String maskDatabaseUrl(String url) {
        // Mask password in connection strings like: protocol://user:password@host:port/db
        return url.replaceAll("(://[^:]+:)[^@]+(@)", "$1****$2");
    }

    private String maskName(String name) {
        String[] parts = name.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) result.append(" ");
            if (parts[i].length() > 0) {
                result.append(parts[i].charAt(0)).append("***");
            }
        }
        return result.toString();
    }

    private String maskAddress(String address) {
        // Keep street number, mask the rest
        return address.replaceAll("([0-9]+\\s+)(.+)", "$1***");
    }

    private String maskGeneric(String value) {
        int len = value.length();
        if (len <= 4) {
            return "****";
        }
        int showChars = Math.min(2, len / 4);
        return value.substring(0, showChars) + "****" + value.substring(len - showChars);
    }

    private String maskHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return "hash:" + encoded.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available, falling back to full mask");
            return "***HASHED***";
        }
    }

    private String maskTokenize(String value) {
        // Check if already tokenized
        String existingToken = reverseTokenStore.get(value);
        if (existingToken != null) {
            return existingToken;
        }

        // Generate new token
        String token = "token:TK" + String.format("%08d", tokenCounter.incrementAndGet());
        tokenStore.put(token, value);
        reverseTokenStore.put(value, token);
        return token;
    }

    private String maskFormatPreserving(String value) {
        StringBuilder result = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isLetter(c)) {
                result.append(Character.isUpperCase(c) ? 'X' : 'x');
            } else if (Character.isDigit(c)) {
                result.append('0');
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String maskJsonField(String input, String fieldName) {
        // Mask JSON fields: "fieldName": "value" → "fieldName": "***"
        // Also handles: "fieldName":"value" (no space)
        String regex = "(\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*)\"[^\"]*\"";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        
        return pattern.matcher(input).replaceAll("$1\"***MASKED***\"");
    }

    private Pattern getOrCompilePattern(String name, String regex) {
        return customPatternCache.computeIfAbsent(name, k -> {
            try {
                return Pattern.compile(regex);
            } catch (Exception e) {
                log.warn("Invalid custom masking pattern '{}': {}", name, e.getMessage());
                return null;
            }
        });
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        
        String lowerKey = key.toLowerCase();
        
        // Check configured keywords
        Set<String> keywords = properties.getMasking().getSensitiveKeywords();
        if (keywords == null || keywords.isEmpty()) {
            keywords = DEFAULT_SENSITIVE_KEYWORDS;
        }
        
        return keywords.stream().anyMatch(keyword -> lowerKey.contains(keyword.toLowerCase()));
    }

    private SensitiveType detectTypeFromKey(String key) {
        String lowerKey = key.toLowerCase();

        // Personal data
        if (lowerKey.contains("email") || lowerKey.contains("mail")) return SensitiveType.EMAIL;
        if (lowerKey.contains("phone") || lowerKey.contains("mobile") || lowerKey.contains("tel")) return SensitiveType.PHONE;
        if (lowerKey.contains("name") && !lowerKey.contains("username")) return SensitiveType.FULL_NAME;
        if (lowerKey.contains("address") || lowerKey.contains("addr")) return SensitiveType.ADDRESS;
        if (lowerKey.contains("birth") || lowerKey.contains("dob")) return SensitiveType.DATE_OF_BIRTH;
        
        // Government IDs
        if (lowerKey.contains("ssn") || lowerKey.contains("social")) return SensitiveType.SSN;
        if (lowerKey.contains("nir") || lowerKey.contains("secu")) return SensitiveType.FRENCH_NIR;
        if (lowerKey.contains("passport")) return SensitiveType.PASSPORT;
        
        // Financial
        if (lowerKey.contains("card") || lowerKey.contains("credit")) return SensitiveType.CREDIT_CARD;
        if (lowerKey.contains("cvv") || lowerKey.contains("cvc")) return SensitiveType.CVV;
        if (lowerKey.contains("iban")) return SensitiveType.IBAN;
        if (lowerKey.contains("account") || lowerKey.contains("bank")) return SensitiveType.BANK_ACCOUNT;
        
        // Authentication
        if (lowerKey.contains("password") || lowerKey.contains("pwd") || lowerKey.contains("passwd")) return SensitiveType.PASSWORD;
        if (lowerKey.contains("jwt")) return SensitiveType.JWT;
        if (lowerKey.contains("bearer")) return SensitiveType.BEARER_TOKEN;
        if (lowerKey.contains("token")) return SensitiveType.OAUTH_TOKEN;
        if (lowerKey.contains("api") && lowerKey.contains("key")) return SensitiveType.API_KEY;
        if (lowerKey.contains("secret")) return SensitiveType.PASSWORD;
        
        // Cloud
        if (lowerKey.contains("aws")) {
            if (lowerKey.contains("secret")) return SensitiveType.AWS_SECRET_KEY;
            return SensitiveType.AWS_ACCESS_KEY;
        }
        if (lowerKey.contains("azure")) return SensitiveType.AZURE_STORAGE_KEY;
        if (lowerKey.contains("gcp") || lowerKey.contains("google")) return SensitiveType.GCP_API_KEY;
        
        // Vault
        if (lowerKey.contains("vault")) return SensitiveType.VAULT_TOKEN;
        
        // Network
        if (lowerKey.contains("ip")) return SensitiveType.IP_ADDRESS;
        
        // Database
        if (lowerKey.contains("connection") || lowerKey.contains("jdbc") || lowerKey.contains("database_url")) {
            return SensitiveType.DATABASE_URL;
        }

        return SensitiveType.GENERIC;
    }

    private boolean isEnabled() {
        return properties.getMasking().isEnabled();
    }

    private MaskingMode getDefaultMode() {
        return properties.getMasking().getDefaultMode();
    }

}

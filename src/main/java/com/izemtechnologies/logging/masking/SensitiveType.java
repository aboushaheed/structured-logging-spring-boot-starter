package com.izemtechnologies.logging.masking;

import java.util.regex.Pattern;

/**
 * Comprehensive enumeration of sensitive data types for GDPR/RGPD compliance.
 * 
 * <p>This enum covers all categories of personally identifiable information (PII),
 * secrets, credentials, and other sensitive data that must be protected in logs.</p>
 * 
 * <h2>Categories Covered:</h2>
 * <ul>
 *   <li><strong>Personal Identity:</strong> Names, emails, phone numbers, addresses</li>
 *   <li><strong>Government IDs:</strong> SSN, passport, driver's license, national IDs</li>
 *   <li><strong>Financial:</strong> Credit cards, bank accounts, IBAN</li>
 *   <li><strong>Authentication:</strong> Passwords, tokens, API keys, secrets</li>
 *   <li><strong>Vault/Secrets Management:</strong> HashiCorp Vault, AWS Secrets, Azure KeyVault</li>
 *   <li><strong>Network:</strong> IP addresses, MAC addresses</li>
 *   <li><strong>Health:</strong> Medical record numbers, health IDs</li>
 * </ul>
 * 
 * @author Souidi
 * @since 1.1.0
 */
public enum SensitiveType {

    // ==================== GENERIC ====================
    
    /**
     * Generic sensitive data without a specific pattern.
     */
    GENERIC(null, "Generic sensitive data"),

    // ==================== PERSONAL IDENTITY (RGPD Article 4) ====================
    
    /**
     * Email addresses - PII under RGPD.
     */
    EMAIL(
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
        "Email address"
    ),

    /**
     * Phone numbers (international format supported) - PII under RGPD.
     */
    PHONE(
        Pattern.compile("(?:\\+|00)[1-9]\\d{0,2}[\\s.-]?(?:\\d[\\s.-]?){6,14}\\d|\\b0[1-9](?:[\\s.-]?\\d{2}){4}\\b"),
        "Phone number"
    ),

    /**
     * Full names (first + last name patterns).
     */
    FULL_NAME(
        Pattern.compile("\\b[A-Z][a-zàâäéèêëïîôùûüç]+\\s+[A-Z][a-zàâäéèêëïîôùûüç]+(?:\\s+[A-Z][a-zàâäéèêëïîôùûüç]+)?\\b"),
        "Full name"
    ),

    /**
     * Postal/mailing addresses.
     */
    ADDRESS(
        Pattern.compile("\\d{1,5}\\s+(?:[A-Za-zÀ-ÿ]+\\s*)+(?:rue|avenue|boulevard|place|chemin|allée|impasse|route|street|road|ave|blvd)\\b", Pattern.CASE_INSENSITIVE),
        "Postal address"
    ),

    /**
     * Date of birth patterns - PII under RGPD.
     */
    DATE_OF_BIRTH(
        Pattern.compile("\\b(?:0[1-9]|[12]\\d|3[01])[/\\-.](?:0[1-9]|1[0-2])[/\\-.](?:19|20)\\d{2}\\b"),
        "Date of birth"
    ),

    // ==================== GOVERNMENT IDENTIFIERS ====================
    
    /**
     * US Social Security Numbers.
     */
    SSN(
        Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
        "US Social Security Number"
    ),

    /**
     * French national ID (Numéro de Sécurité Sociale / NIR).
     */
    FRENCH_NIR(
        Pattern.compile("\\b[12]\\s?\\d{2}\\s?(?:0[1-9]|1[0-2]|[2-9]\\d)\\s?(?:0[1-9]|[1-8]\\d|9[0-5]|2[AB])\\s?\\d{3}\\s?\\d{3}\\s?(?:\\d{2})?\\b"),
        "French NIR (Sécurité Sociale)"
    ),

    /**
     * French national ID card number.
     */
    FRENCH_CNI(
        Pattern.compile("\\b[A-Z0-9]{12}\\b"),
        "French CNI number"
    ),

    /**
     * Passport numbers (generic international format).
     */
    PASSPORT(
        Pattern.compile("\\b[A-Z]{1,2}\\d{6,9}\\b"),
        "Passport number"
    ),

    /**
     * Driver's license numbers.
     */
    DRIVERS_LICENSE(
        Pattern.compile("\\b[A-Z0-9]{5,15}\\b"),
        "Driver's license"
    ),

    /**
     * German Tax ID (Steueridentifikationsnummer).
     */
    GERMAN_TAX_ID(
        Pattern.compile("\\b\\d{11}\\b"),
        "German Tax ID"
    ),

    /**
     * UK National Insurance Number.
     */
    UK_NIN(
        Pattern.compile("\\b[A-CEGHJ-PR-TW-Z]{2}\\d{6}[A-D]\\b"),
        "UK National Insurance Number"
    ),

    // ==================== FINANCIAL DATA (PCI-DSS) ====================
    
    /**
     * Credit card numbers (13-19 digits, with optional separators).
     * Covers Visa, MasterCard, Amex, Discover, etc.
     */
    CREDIT_CARD(
        Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12}|(?:2131|1800|35\\d{3})\\d{11})\\b"),
        "Credit card number"
    ),

    /**
     * Credit card with separators.
     */
    CREDIT_CARD_FORMATTED(
        Pattern.compile("\\b(?:\\d{4}[\\s-]?){3}\\d{4}\\b"),
        "Credit card (formatted)"
    ),

    /**
     * CVV/CVC security codes.
     */
    CVV(
        Pattern.compile("\\b\\d{3,4}\\b"),
        "CVV/CVC code"
    ),

    /**
     * IBAN (International Bank Account Number).
     */
    IBAN(
        Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{7}(?:[A-Z0-9]?){0,16}\\b"),
        "IBAN"
    ),

    /**
     * French RIB (Relevé d'Identité Bancaire).
     */
    FRENCH_RIB(
        Pattern.compile("\\b\\d{5}\\s?\\d{5}\\s?[A-Z0-9]{11}\\s?\\d{2}\\b"),
        "French RIB"
    ),

    /**
     * BIC/SWIFT codes.
     */
    BIC_SWIFT(
        Pattern.compile("\\b[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}(?:[A-Z0-9]{3})?\\b"),
        "BIC/SWIFT code"
    ),

    /**
     * Bank account numbers (generic).
     */
    BANK_ACCOUNT(
        Pattern.compile("\\b\\d{8,17}\\b"),
        "Bank account number"
    ),

    // ==================== AUTHENTICATION & SECRETS ====================
    
    /**
     * Passwords in various formats (key=value, JSON, etc.).
     */
    PASSWORD(
        Pattern.compile("(?i)(?:password|passwd|pwd|pass|secret|mot_de_passe)[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}\\]]+)"),
        "Password"
    ),

    /**
     * JWT tokens (JSON Web Tokens).
     */
    JWT(
        Pattern.compile("eyJ[a-zA-Z0-9_-]*\\.eyJ[a-zA-Z0-9_-]*\\.[a-zA-Z0-9_-]*"),
        "JWT token"
    ),

    /**
     * Bearer tokens in Authorization headers.
     */
    BEARER_TOKEN(
        Pattern.compile("(?i)Bearer\\s+[a-zA-Z0-9_\\-\\.]+"),
        "Bearer token"
    ),

    /**
     * Basic Auth credentials (base64 encoded).
     */
    BASIC_AUTH(
        Pattern.compile("(?i)Basic\\s+[a-zA-Z0-9+/]+=*"),
        "Basic Auth credentials"
    ),

    /**
     * Generic API keys.
     */
    API_KEY(
        Pattern.compile("(?i)(?:api[_-]?key|apikey|x-api-key)[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9_\\-]{16,})"),
        "API key"
    ),

    /**
     * OAuth tokens and secrets.
     */
    OAUTH_TOKEN(
        Pattern.compile("(?i)(?:oauth[_-]?token|access[_-]?token|refresh[_-]?token)[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9_\\-\\.]+)"),
        "OAuth token"
    ),

    /**
     * Session IDs.
     */
    SESSION_ID(
        Pattern.compile("(?i)(?:session[_-]?id|jsessionid|phpsessid|asp\\.net_sessionid)[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9_\\-]+)"),
        "Session ID"
    ),

    // ==================== CLOUD PROVIDER SECRETS ====================
    
    /**
     * AWS Access Key ID.
     */
    AWS_ACCESS_KEY(
        Pattern.compile("(?:AKIA|ABIA|ACCA|ASIA)[A-Z0-9]{16}"),
        "AWS Access Key ID"
    ),

    /**
     * AWS Secret Access Key.
     */
    AWS_SECRET_KEY(
        Pattern.compile("(?i)(?:aws[_-]?secret[_-]?(?:access[_-]?)?key)[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9/+=]{40})"),
        "AWS Secret Access Key"
    ),

    /**
     * AWS Session Token.
     */
    AWS_SESSION_TOKEN(
        Pattern.compile("(?i)(?:aws[_-]?session[_-]?token)[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9/+=]+)"),
        "AWS Session Token"
    ),

    /**
     * Azure Storage Account Key.
     */
    AZURE_STORAGE_KEY(
        Pattern.compile("(?i)(?:azure[_-]?storage[_-]?(?:account[_-]?)?key|accountkey)[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9+/]{86}==)"),
        "Azure Storage Key"
    ),

    /**
     * Azure Connection String.
     */
    AZURE_CONNECTION_STRING(
        Pattern.compile("(?i)DefaultEndpointsProtocol=https?;AccountName=[^;]+;AccountKey=[a-zA-Z0-9+/]+=*;"),
        "Azure Connection String"
    ),

    /**
     * Azure Client Secret.
     */
    AZURE_CLIENT_SECRET(
        Pattern.compile("(?i)(?:azure[_-]?client[_-]?secret|client[_-]?secret)[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9_\\-\\.~]{34,})"),
        "Azure Client Secret"
    ),

    /**
     * Google Cloud API Key.
     */
    GCP_API_KEY(
        Pattern.compile("AIza[a-zA-Z0-9_\\-]{35}"),
        "Google Cloud API Key"
    ),

    /**
     * Google Cloud Service Account Key (JSON).
     */
    GCP_SERVICE_ACCOUNT(
        Pattern.compile("(?i)\"private_key\"\\s*:\\s*\"-----BEGIN (?:RSA )?PRIVATE KEY-----[^\"]+-----END (?:RSA )?PRIVATE KEY-----\""),
        "GCP Service Account Key"
    ),

    // ==================== VAULT & SECRETS MANAGEMENT ====================
    
    /**
     * HashiCorp Vault Token.
     */
    VAULT_TOKEN(
        Pattern.compile("(?i)(?:vault[_-]?token|x-vault-token)[\"']?\\s*[:=]\\s*[\"']?(?:hvs\\.[a-zA-Z0-9_\\-]+|s\\.[a-zA-Z0-9]{24})"),
        "HashiCorp Vault Token"
    ),

    /**
     * HashiCorp Vault Secret Path.
     */
    VAULT_SECRET_PATH(
        Pattern.compile("(?i)(?:vault[_-]?(?:secret[_-]?)?path)[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9/_\\-]+)"),
        "Vault Secret Path"
    ),

    /**
     * Kubernetes Secret (base64 encoded).
     */
    K8S_SECRET(
        Pattern.compile("(?i)(?:data|stringData):\\s*\\n(?:\\s+[a-zA-Z0-9_\\-]+:\\s*[a-zA-Z0-9+/]+=*\\s*\\n?)+"),
        "Kubernetes Secret"
    ),

    /**
     * Docker Registry Auth.
     */
    DOCKER_AUTH(
        Pattern.compile("(?i)(?:docker[_-]?(?:registry[_-]?)?(?:auth|password))[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)"),
        "Docker Registry Auth"
    ),

    /**
     * GitHub Token (Personal Access Token, OAuth, App).
     */
    GITHUB_TOKEN(
        Pattern.compile("(?:ghp|gho|ghu|ghs|ghr)_[a-zA-Z0-9]{36,}"),
        "GitHub Token"
    ),

    /**
     * GitLab Token.
     */
    GITLAB_TOKEN(
        Pattern.compile("glpat-[a-zA-Z0-9_\\-]{20,}"),
        "GitLab Token"
    ),

    /**
     * NPM Token.
     */
    NPM_TOKEN(
        Pattern.compile("npm_[a-zA-Z0-9]{36}"),
        "NPM Token"
    ),

    /**
     * Slack Token (Bot, User, Webhook).
     */
    SLACK_TOKEN(
        Pattern.compile("xox[baprs]-[a-zA-Z0-9\\-]+"),
        "Slack Token"
    ),

    /**
     * Stripe API Key.
     */
    STRIPE_KEY(
        Pattern.compile("(?:sk|pk|rk)_(?:live|test)_[a-zA-Z0-9]{24,}"),
        "Stripe API Key"
    ),

    /**
     * Twilio Auth Token.
     */
    TWILIO_TOKEN(
        Pattern.compile("(?i)(?:twilio[_-]?(?:auth[_-]?)?token)[\"']?\\s*[:=]\\s*[\"']?([a-f0-9]{32})"),
        "Twilio Auth Token"
    ),

    /**
     * SendGrid API Key.
     */
    SENDGRID_KEY(
        Pattern.compile("SG\\.[a-zA-Z0-9_\\-]{22}\\.[a-zA-Z0-9_\\-]{43}"),
        "SendGrid API Key"
    ),

    /**
     * Mailchimp API Key.
     */
    MAILCHIMP_KEY(
        Pattern.compile("[a-f0-9]{32}-us\\d{1,2}"),
        "Mailchimp API Key"
    ),

    // ==================== ENCRYPTION & CERTIFICATES ====================
    
    /**
     * Private Keys (RSA, EC, etc.).
     */
    PRIVATE_KEY(
        Pattern.compile("-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----[\\s\\S]+?-----END (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----"),
        "Private Key"
    ),

    /**
     * Encryption Keys (AES, etc.).
     */
    ENCRYPTION_KEY(
        Pattern.compile("(?i)(?:encryption[_-]?key|aes[_-]?key|secret[_-]?key)[\"']?\\s*[:=]\\s*[\"']?([a-fA-F0-9]{32,64})"),
        "Encryption Key"
    ),

    /**
     * Database Connection Strings with credentials.
     */
    DATABASE_URL(
        Pattern.compile("(?i)(?:mysql|postgres(?:ql)?|mongodb(?:\\+srv)?|redis|oracle|sqlserver)://[^:]+:[^@]+@[^\\s\"']+"),
        "Database Connection URL"
    ),

    // ==================== NETWORK IDENTIFIERS ====================
    
    /**
     * IPv4 addresses.
     */
    IP_ADDRESS(
        Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"),
        "IPv4 address"
    ),

    /**
     * IPv6 addresses.
     */
    IP_ADDRESS_V6(
        Pattern.compile("\\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\b|\\b(?:[0-9a-fA-F]{1,4}:){1,7}:|\\b(?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}\\b"),
        "IPv6 address"
    ),

    /**
     * MAC addresses.
     */
    MAC_ADDRESS(
        Pattern.compile("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b"),
        "MAC address"
    ),

    // ==================== HEALTH DATA (RGPD Special Category) ====================
    
    /**
     * Medical Record Numbers.
     */
    MEDICAL_RECORD(
        Pattern.compile("(?i)(?:mrn|medical[_-]?record[_-]?(?:number|num|no)?)[\"']?\\s*[:=]\\s*[\"']?([A-Z0-9]{6,15})"),
        "Medical Record Number"
    ),

    /**
     * Health Insurance Numbers.
     */
    HEALTH_INSURANCE_ID(
        Pattern.compile("(?i)(?:health[_-]?(?:insurance[_-]?)?id|insurance[_-]?number)[\"']?\\s*[:=]\\s*[\"']?([A-Z0-9]{8,20})"),
        "Health Insurance ID"
    ),

    // ==================== CUSTOM ====================
    
    /**
     * Custom pattern (user-defined via configuration).
     */
    CUSTOM(null, "Custom pattern");

    private final Pattern pattern;
    private final String description;

    SensitiveType(Pattern pattern, String description) {
        this.pattern = pattern;
        this.description = description;
    }

    /**
     * Returns the detection pattern for this sensitive type.
     * 
     * @return the regex pattern, or null for GENERIC and CUSTOM types
     */
    public Pattern getPattern() {
        return pattern;
    }

    /**
     * Returns a human-readable description of this sensitive type.
     * 
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this type has an associated pattern.
     * 
     * @return true if a pattern is defined
     */
    public boolean hasPattern() {
        return pattern != null;
    }

    /**
     * Checks if this type is a RGPD/GDPR personal data category.
     * 
     * @return true if this is personal data under RGPD
     */
    public boolean isPersonalData() {
        return switch (this) {
            case EMAIL, PHONE, FULL_NAME, ADDRESS, DATE_OF_BIRTH,
                 SSN, FRENCH_NIR, FRENCH_CNI, PASSPORT, DRIVERS_LICENSE,
                 GERMAN_TAX_ID, UK_NIN, IP_ADDRESS, IP_ADDRESS_V6,
                 MEDICAL_RECORD, HEALTH_INSURANCE_ID -> true;
            default -> false;
        };
    }

    /**
     * Checks if this type is a secret/credential.
     * 
     * @return true if this is a secret or credential
     */
    public boolean isSecret() {
        return switch (this) {
            case PASSWORD, JWT, BEARER_TOKEN, BASIC_AUTH, API_KEY, OAUTH_TOKEN,
                 SESSION_ID, AWS_ACCESS_KEY, AWS_SECRET_KEY, AWS_SESSION_TOKEN,
                 AZURE_STORAGE_KEY, AZURE_CONNECTION_STRING, AZURE_CLIENT_SECRET,
                 GCP_API_KEY, GCP_SERVICE_ACCOUNT, VAULT_TOKEN, VAULT_SECRET_PATH,
                 K8S_SECRET, DOCKER_AUTH, GITHUB_TOKEN, GITLAB_TOKEN, NPM_TOKEN,
                 SLACK_TOKEN, STRIPE_KEY, TWILIO_TOKEN, SENDGRID_KEY, MAILCHIMP_KEY,
                 PRIVATE_KEY, ENCRYPTION_KEY, DATABASE_URL -> true;
            default -> false;
        };
    }

    /**
     * Checks if this type is financial data (PCI-DSS).
     * 
     * @return true if this is financial data
     */
    public boolean isFinancialData() {
        return switch (this) {
            case CREDIT_CARD, CREDIT_CARD_FORMATTED, CVV, IBAN,
                 FRENCH_RIB, BIC_SWIFT, BANK_ACCOUNT -> true;
            default -> false;
        };
    }

    /**
     * Checks if this type is health-related (RGPD special category).
     * 
     * @return true if this is health data
     */
    public boolean isHealthData() {
        return switch (this) {
            case MEDICAL_RECORD, HEALTH_INSURANCE_ID -> true;
            default -> false;
        };
    }
}

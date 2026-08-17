package com.ecom.sso;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings for KKU Single Sign On (SSONext), per the university's integration
 * guide.
 *
 * <p>Two stages exist with different hosts. {@link #getStage()} selects which
 * pair is used, so switching from UAT to production is a one-line change rather
 * than an edit spread over four URLs.
 */
@Component
@ConfigurationProperties(prefix = "kku.sso")
public class KkuSsoProperties {

    /** {@code uat} or {@code prod}. */
    private String stage = "uat";

    private String appId = "";
    private String clientId = "";
    private String clientSecret = "";

    /**
     * Must match the Redirect URL registered on the KKU SSO request form —
     * it is sent again during the token exchange and the provider compares it.
     */
    private String redirectLoginUrl = "";
    private String redirectLogoutUrl = "";

    private String prodWebBaseUrl = "https://ssonext.kku.ac.th";
    private String prodApiBaseUrl = "https://ssonext-api.kku.ac.th";
    private String uatWebBaseUrl = "https://sso-uat-web.kku.ac.th";
    private String uatApiBaseUrl = "https://sso-uat-api.kku.ac.th";

    public boolean isProd() {
        return "prod".equalsIgnoreCase(stage) || "production".equalsIgnoreCase(stage);
    }

    public String webBaseUrl() {
        return isProd() ? prodWebBaseUrl : uatWebBaseUrl;
    }

    public String apiBaseUrl() {
        return isProd() ? prodApiBaseUrl : uatApiBaseUrl;
    }

    /** Where to send the browser to start a login. */
    public String loginUrl() {
        return webBaseUrl() + "/login?app=" + appId;
    }

    /** Where to send the browser to end the SSO session, not just ours. */
    public String logoutUrl() {
        return webBaseUrl() + "/logout?app=" + appId;
    }

    public String tokenEndpoint() {
        return apiBaseUrl() + "/auth.token";
    }

    public String profileEndpoint() {
        return apiBaseUrl() + "/user.profile";
    }

    public String statusEndpoint() {
        return apiBaseUrl() + "/auth.status";
    }

    /** True when every value the flow needs is present. */
    public boolean isConfigured() {
        return notBlank(appId) && notBlank(clientId) && notBlank(clientSecret)
                && notBlank(redirectLoginUrl);
    }

    /** Names the missing settings, for a startup warning that is actually actionable. */
    public String describeMissing() {
        StringBuilder sb = new StringBuilder();
        if (!notBlank(appId)) {
            sb.append("kku.sso.app-id ");
        }
        if (!notBlank(clientId)) {
            sb.append("kku.sso.client-id ");
        }
        if (!notBlank(clientSecret)) {
            sb.append("kku.sso.client-secret ");
        }
        if (!notBlank(redirectLoginUrl)) {
            sb.append("kku.sso.redirect-login-url ");
        }
        return sb.toString().trim();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectLoginUrl() {
        return redirectLoginUrl;
    }

    public void setRedirectLoginUrl(String redirectLoginUrl) {
        this.redirectLoginUrl = redirectLoginUrl;
    }

    public String getRedirectLogoutUrl() {
        return redirectLogoutUrl;
    }

    public void setRedirectLogoutUrl(String redirectLogoutUrl) {
        this.redirectLogoutUrl = redirectLogoutUrl;
    }

    public String getProdWebBaseUrl() {
        return prodWebBaseUrl;
    }

    public void setProdWebBaseUrl(String prodWebBaseUrl) {
        this.prodWebBaseUrl = prodWebBaseUrl;
    }

    public String getProdApiBaseUrl() {
        return prodApiBaseUrl;
    }

    public void setProdApiBaseUrl(String prodApiBaseUrl) {
        this.prodApiBaseUrl = prodApiBaseUrl;
    }

    public String getUatWebBaseUrl() {
        return uatWebBaseUrl;
    }

    public void setUatWebBaseUrl(String uatWebBaseUrl) {
        this.uatWebBaseUrl = uatWebBaseUrl;
    }

    public String getUatApiBaseUrl() {
        return uatApiBaseUrl;
    }

    public void setUatApiBaseUrl(String uatApiBaseUrl) {
        this.uatApiBaseUrl = uatApiBaseUrl;
    }
}

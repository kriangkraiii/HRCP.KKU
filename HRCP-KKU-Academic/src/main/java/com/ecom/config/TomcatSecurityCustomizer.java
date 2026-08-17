package com.ecom.config;

import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat-level hardening that Spring Security cannot reach.
 *
 * <p>
 * Two separate concerns live here:
 *
 * <p>
 * <b>1. TRACE handling.</b> By default Tomcat's Connector has allowTrace=false,
 * which means TRACE is intercepted and rejected BEFORE any Filter (including
 * SecurityHeadersFilter) gets a chance to process it. That produces a different
 * 405 response (body, Allow header listing all methods, JSESSIONID cookie) than
 * the filter's uniform empty 405 — creating the Observable Response Discrepancy
 * (CWE-204) that OWASP ZAP reports as "Proxy Disclosure" (Alert 40025).
 * Enabling allowTrace lets the request reach SecurityHeadersFilter, which
 * answers TRACE, TRACK and OPTIONS identically.
 *
 * <p>
 * <b>This means TRACE is not blocked at the connector — SecurityHeadersFilter
 * is the only thing refusing it.</b> That filter must keep running first. If
 * its {@code @Order(Ordered.HIGHEST_PRECEDENCE)} is ever changed or the filter
 * is removed, TRACE becomes live on the application. Change one, check the
 * other.
 *
 * <p>
 * <b>2. Error page fingerprinting.</b> Tomcat's default ErrorReportValve prints
 * its own version and a stack report on any error it renders itself, which
 * identifies the exact server build to a scanner.
 */
@Configuration
public class TomcatSecurityCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector -> {
            // Let TRACE through to our SecurityHeadersFilter
            connector.setAllowTrace(true);
        });

        factory.addContextCustomizers(context -> {
            ErrorReportValve valve = new ErrorReportValve();
            // Drop the "Apache Tomcat/x.y.z" footer.
            valve.setShowServerInfo(false);
            // Drop the exception/stack report body.
            valve.setShowReport(false);
            context.getParent().getPipeline().addValve(valve);
        });
    }
}

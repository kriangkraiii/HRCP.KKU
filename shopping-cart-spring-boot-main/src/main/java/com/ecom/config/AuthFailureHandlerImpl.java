package com.ecom.config;

import java.io.IOException;
import java.net.URLEncoder;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthFailureHandlerImpl extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String errorMessage = "Invalid Username or Password";

        if (exception instanceof LockedException) {
            errorMessage = "Your account is locked! Failed attempt 3";
        }

        setDefaultFailureUrl("/signin?error");
        super.onAuthenticationFailure(request, response, exception);
        
        request.getSession().setAttribute("errorMessage", errorMessage);
    }
}

package com.ecom.config;

import java.util.TimeZone;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final RequestLoggingInterceptor requestLoggingInterceptor;

	@org.springframework.beans.factory.annotation.Value("${app.upload.dir:${user.dir}/uploads/}")
	private String uploadBaseDir;

	public WebConfig(RequestLoggingInterceptor requestLoggingInterceptor) {
		this.requestLoggingInterceptor = requestLoggingInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(requestLoggingInterceptor)
				.addPathPatterns("/**")
				.excludePathPatterns("/css/**", "/js/**", "/img/**", "/uploads/**",
						"/admin/css/**", "/admin/js/**", "/webjars/**", "/favicon.ico");
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// Admin static resources
		registry.addResourceHandler("/admin/js/**").addResourceLocations("classpath:/static/admin/js/");
		registry.addResourceHandler("/admin/css/**").addResourceLocations("classpath:/static/admin/css/")
				.setCachePeriod(0);

		// Third-party assets (Bootstrap, Font Awesome, Sarabun) served from our
		// own origin instead of a CDN: the app runs on an internal network where
		// outbound access is not guaranteed, and self-hosting removes the
		// subresource-integrity and third-party-script exposure entirely.
		registry.addResourceHandler("/vendor/**")
				.addResourceLocations("classpath:/static/vendor/")
				.setCachePeriod(86400);

		// General static resources
		registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");
		registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/");
		registry.addResourceHandler("/img/**").addResourceLocations("classpath:/static/img/")
				.setCacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic());

		// Normalize upload base directory
		String base = uploadBaseDir.replace('\\', '/');
		if (!base.endsWith("/")) {
			base += "/";
		}

		// Profile images - serve from both external uploads and static directory with browser HTTP caching (7 days)
		String profileUploadPath = base + "profile_img/";
		registry.addResourceHandler("/img/profile_img/**")
				.addResourceLocations("file:" + profileUploadPath, "classpath:/static/img/profile_img/")
				.setCacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic().mustRevalidate());

		// Category images from external uploads directory
		String categoryUploadPath = base + "category_img/";
		registry.addResourceHandler("/img/category_img/**")
				.addResourceLocations("file:" + categoryUploadPath, "classpath:/static/img/category_img/")
				.setCacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic());

		// Product images from external uploads directory
		String productUploadPath = base + "product_img/";
		registry.addResourceHandler("/img/product_img/**")
				.addResourceLocations("file:" + productUploadPath, "classpath:/static/img/product_img/")
				.setCacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic());

		// Serve uploaded files from external directory with caching
		registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + base)
				.setCacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic());
	}

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Bangkok"));
	}
}

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
				.excludePathPatterns("/css/**", "/js/**", "/img/**",
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

		// Normalize upload base directory to proper URI (safely encodes spaces like 'Spring pj' on Mac)
		java.nio.file.Path basePath = java.nio.file.Path.of(uploadBaseDir).toAbsolutePath().normalize();

		// Profile images - serve from both external uploads and static directory with browser HTTP caching (7 days)
		String profileUploadUri = basePath.resolve("profile_img").toUri().toString();
		if (!profileUploadUri.endsWith("/")) {
			profileUploadUri += "/";
		}
		registry.addResourceHandler("/img/profile_img/**")
				.addResourceLocations(profileUploadUri, "file:uploads/profile_img/", "classpath:/static/img/profile_img/")
				.setCacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic().mustRevalidate());

		// Category images from external uploads directory
		String categoryUploadUri = basePath.resolve("category_img").toUri().toString();
		if (!categoryUploadUri.endsWith("/")) {
			categoryUploadUri += "/";
		}
		registry.addResourceHandler("/img/category_img/**")
				.addResourceLocations(categoryUploadUri, "file:uploads/category_img/", "classpath:/static/img/category_img/")
				.setCacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic());

		// Product images from external uploads directory
		String productUploadUri = basePath.resolve("product_img").toUri().toString();
		if (!productUploadUri.endsWith("/")) {
			productUploadUri += "/";
		}
		registry.addResourceHandler("/img/product_img/**")
				.addResourceLocations(productUploadUri, "file:uploads/product_img/", "classpath:/static/img/product_img/")
				.setCacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic());

		// General static resources
		registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");
		// Our own scripts are served without their comments (JsCommentStripper);
		// third-party files under /vendor are left exactly as published.
		registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/")
				.resourceChain(false)
				.addTransformer(new CommentStrippingResourceTransformer());
		registry.addResourceHandler("/img/**").addResourceLocations("classpath:/static/img/")
				.setCacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic());

		// Pinned here rather than left to spring.web.resources.static-locations: the
		// production properties file is not in git, and an old copy of it pointed
		// /static/** (open to anonymous users) at file:uploads/. Registering the
		// pattern ourselves takes precedence over Boot's handler for it.
		registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");

		// The upload root is deliberately NOT served as a whole. It holds .p12
		// certificates, signature images, signed documents and applicants'
		// attachments; each of those is read back through a controller that checks
		// who is asking. Only the image folders above are public.
	}

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Bangkok"));
	}
}

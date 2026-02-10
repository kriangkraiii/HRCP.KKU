package com.ecom.config;

import java.util.TimeZone;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// Admin static resources
		registry.addResourceHandler("/admin/js/**").addResourceLocations("classpath:/static/admin/js/");
		registry.addResourceHandler("/admin/css/**").addResourceLocations("classpath:/static/admin/css/")
				.setCachePeriod(0);

		// General static resources
		registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");
		registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/");
		registry.addResourceHandler("/img/**").addResourceLocations("classpath:/static/img/").setCachePeriod(3600);

		// Profile images - serve from both external uploads and static directory
		String profileUploadPath = System.getProperty("user.dir") + "/uploads/profile_img/";
		registry.addResourceHandler("/img/profile_img/**")
				.addResourceLocations("file:" + profileUploadPath, "classpath:/static/img/profile_img/")
				.setCachePeriod(0);

		// Category images from external uploads directory
		String categoryUploadPath = System.getProperty("user.dir") + "/uploads/category_img/";
		registry.addResourceHandler("/img/category_img/**")
				.addResourceLocations("file:" + categoryUploadPath, "classpath:/static/img/category_img/")
				.setCachePeriod(3600);

		// Product images from external uploads directory
		String productUploadPath = System.getProperty("user.dir") + "/uploads/product_img/";
		registry.addResourceHandler("/img/product_img/**")
				.addResourceLocations("file:" + productUploadPath, "classpath:/static/img/product_img/")
				.setCachePeriod(3600);

		// Serve uploaded files from external directory
		String uploadDir = System.getProperty("user.dir") + "/uploads/";

		registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + uploadDir);
	}
	 @PostConstruct
	    public void init() {
	        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Bangkok"));
	    }
}

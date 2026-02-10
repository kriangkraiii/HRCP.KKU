package com.ecom.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.ecom.service.SiteSettingService;

@ControllerAdvice
public class GlobalModelAdvice {

    @Autowired
    private SiteSettingService siteSettingService;

    @Value("${aws.s3.bucket.category}")
    private String categoryBucket;

    @Value("${aws.s3.bucket.product}")
    private String productBucket;

    @Value("${aws.s3.bucket.profile}")
    private String profileBucket;

    /**
     * Exposes imageMode ("AWS" or "LOCAL") to all Thymeleaf templates
     */
    @ModelAttribute("imageMode")
    public String imageMode() {
        return siteSettingService.getImageMode();
    }

    /**
     * Expose bucket names so JS can map AWS URLs to local paths
     */
    @ModelAttribute("awsCategoryBucket")
    public String awsCategoryBucket() {
        return categoryBucket;
    }

    @ModelAttribute("awsProductBucket")
    public String awsProductBucket() {
        return productBucket;
    }

    @ModelAttribute("awsProfileBucket")
    public String awsProfileBucket() {
        return profileBucket;
    }
}

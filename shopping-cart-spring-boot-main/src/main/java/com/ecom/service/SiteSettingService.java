package com.ecom.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ecom.model.SiteSetting;
import com.ecom.repository.SiteSettingRepository;

@Service
public class SiteSettingService {

    @Autowired
    private SiteSettingRepository siteSettingRepository;

    private static final String IMAGE_MODE_KEY = "imageMode";
    private static final String DEFAULT_MODE = "AWS"; // Default to AWS

    /**
     * Get the current image mode: "AWS" or "LOCAL"
     */
    public String getImageMode() {
        Optional<SiteSetting> setting = siteSettingRepository.findByKey(IMAGE_MODE_KEY);
        return setting.map(SiteSetting::getValue).orElse(DEFAULT_MODE);
    }

    /**
     * Set image mode to "AWS" or "LOCAL"
     */
    public String setImageMode(String mode) {
        if (!"AWS".equalsIgnoreCase(mode) && !"LOCAL".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Invalid image mode: " + mode + ". Must be AWS or LOCAL.");
        }

        String normalizedMode = mode.toUpperCase();
        Optional<SiteSetting> existing = siteSettingRepository.findByKey(IMAGE_MODE_KEY);

        if (existing.isPresent()) {
            SiteSetting setting = existing.get();
            setting.setValue(normalizedMode);
            siteSettingRepository.save(setting);
        } else {
            siteSettingRepository.save(new SiteSetting(IMAGE_MODE_KEY, normalizedMode));
        }

        return normalizedMode;
    }

    /**
     * Toggle between AWS and LOCAL
     */
    public String toggleImageMode() {
        String current = getImageMode();
        String newMode = "AWS".equals(current) ? "LOCAL" : "AWS";
        return setImageMode(newMode);
    }
}

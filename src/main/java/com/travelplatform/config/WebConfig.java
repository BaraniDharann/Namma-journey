package com.travelplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload.dir:uploads/drivers}")
    private String uploadDir;

    // Whitelist of file extensions allowed to be served from upload directories.
    // Any other extension is blocked so an attacker who slips a .html/.svg/.exe past the
    // upload validator can't trigger script execution or trojan downloads via the resource handler.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        PathResourceResolver imageOnly = new PathResourceResolver() {
            @Override
            protected Resource getResource(String resourcePath, Resource location) throws IOException {
                if (!hasAllowedExtension(resourcePath)) {
                    return null;
                }
                return super.getResource(resourcePath, location);
            }
        };

        registry.addResourceHandler("/uploads/drivers/**")
                .addResourceLocations("file:" + uploadDir + "/")
                .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .resourceChain(true)
                .addResolver(imageOnly);

        registry.addResourceHandler("/driverphoto/**")
                .addResourceLocations("file:driverphoto/")
                .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .resourceChain(true)
                .addResolver(imageOnly);
    }

    private static boolean hasAllowedExtension(String resourcePath) {
        int dot = resourcePath.lastIndexOf('.');
        if (dot < 0 || dot == resourcePath.length() - 1) return false;
        String ext = resourcePath.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.contains(ext);
    }
}

package com.ecom.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Serves our own scripts under {@code /js} without their comments; see
 * {@link JsCommentStripper}. The result is kept per file and redone when the
 * file changes, so editing a script during development shows up on reload.
 */
public class CommentStrippingResourceTransformer implements ResourceTransformer {

    private record Stripped(long lastModified, byte[] content) {
    }

    private final Map<String, Stripped> cache = new ConcurrentHashMap<>();

    @Override
    public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
            throws IOException {
        resource = chain.transform(request, resource);
        String name = resource.getFilename();
        if (name == null || !name.endsWith(".js")) {
            return resource;
        }
        String key = resource.getURL().toString();
        long lastModified = resource.lastModified();
        Stripped stripped = cache.get(key);
        if (stripped == null || stripped.lastModified() != lastModified) {
            String source = resource.getContentAsString(StandardCharsets.UTF_8);
            stripped = new Stripped(lastModified, JsCommentStripper.strip(source).getBytes(StandardCharsets.UTF_8));
            cache.put(key, stripped);
        }
        return new TransformedResource(resource, stripped.content());
    }
}

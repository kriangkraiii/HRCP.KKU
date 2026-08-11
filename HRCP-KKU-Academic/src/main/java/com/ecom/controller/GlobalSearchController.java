package com.ecom.controller;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ecom.model.UserDtls;
import com.ecom.service.GlobalSearchService;
import com.ecom.service.GlobalSearchService.SearchResultItem;
import com.ecom.service.UserService;

@Controller
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;
    private final UserService userService;

    public GlobalSearchController(GlobalSearchService globalSearchService, UserService userService) {
        this.globalSearchService = globalSearchService;
        this.userService = userService;
    }

    @GetMapping("/api/global-search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(name = "q", defaultValue = "") String query,
            Principal principal) {
        Map<String, Object> resp = new HashMap<>();
        if (principal == null) {
            resp.put("results", Collections.emptyList());
            return ResponseEntity.status(401).body(resp);
        }

        UserDtls user = userService.getUserByEmail(principal.getName());
        if (user == null) {
            resp.put("results", Collections.emptyList());
            return ResponseEntity.status(401).body(resp);
        }

        List<SearchResultItem> results = globalSearchService.search(query, user);
        resp.put("query", query);
        resp.put("results", results);
        resp.put("total", results.size());
        return ResponseEntity.ok(resp);
    }
}

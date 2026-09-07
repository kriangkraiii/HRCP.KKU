package com.ecom.search.config;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.ecom.search.repository.SearchDocumentQueryRepository;
import com.ecom.search.repository.SearchSqlBuilder;

/**
 * Decides once, at startup, which SQL dialect the search speaks.
 *
 * <p>Detected from the connection rather than configured, because getting it
 * wrong is silent in the worst way: emitting PostgreSQL SQL against H2 fails
 * loudly, but emitting the portable form against PostgreSQL would work
 * perfectly while quietly abandoning the trigram index, the full-text ranking
 * and the typo-tolerant tier. Everything would still return results, just worse
 * ones, and no test would fail.
 */
@Configuration
public class SearchConfig {

    private static final Logger log = LoggerFactory.getLogger(SearchConfig.class);

    @Bean
    SearchSqlBuilder searchSqlBuilder(DataSource dataSource) {
        SearchSqlBuilder.Flavor flavor = detect(dataSource);
        log.info("Search SQL flavour: {}", flavor);
        return new SearchSqlBuilder(flavor);
    }

    @Bean
    SearchDocumentQueryRepository searchDocumentQueryRepository(
            NamedParameterJdbcTemplate jdbcTemplate, SearchSqlBuilder builder) {
        return new SearchDocumentQueryRepository(jdbcTemplate, builder);
    }

    private SearchSqlBuilder.Flavor detect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product != null && product.toLowerCase().contains("postgres")) {
                return SearchSqlBuilder.Flavor.POSTGRES;
            }
            return SearchSqlBuilder.Flavor.PORTABLE;
        } catch (SQLException e) {
            // Falling back to the portable form keeps the application starting;
            // the log line is what tells an operator the search is degraded.
            log.warn("ตรวจชนิดฐานข้อมูลไม่ได้ ใช้ SQL แบบ portable แทน: {}", e.toString());
            return SearchSqlBuilder.Flavor.PORTABLE;
        }
    }
}

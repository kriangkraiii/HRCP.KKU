package com.ecom.search.dto;

/**
 * One filter option and how many results carry it.
 *
 * @param key      the value to filter on, as stored
 * @param label    what to show — the Thai category or status label
 * @param count    how many hits match it
 * @param selected whether the current query already filters on it
 */
public record SearchFacet(String key, String label, long count, boolean selected) {
}

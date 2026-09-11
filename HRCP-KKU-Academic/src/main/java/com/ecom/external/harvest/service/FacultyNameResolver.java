package com.ecom.external.harvest.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.ecom.external.model.FsFaculty;
import com.ecom.external.service.EnglishNameSplitter;

/**
 * High-precision resolver and variant generator for faculty names across all
 * academic publication sources (Crossref, OpenAlex, DBLP, ThaiJO, KKU IR).
 *
 * <p>Handles:
 * <ul>
 *   <li>3-token names (First Middle Last vs First Last vs First Middle)</li>
 *   <li>Hyphenated surnames (e.g. So-In vs Soin, Arch-int vs Archint)</li>
 *   <li>Inverted catalog formats (e.g. "Lastname, Firstname" in ThaiJO / DSpace)</li>
 *   <li>Author initials (e.g. "K. Saikaew", "C. So-In", "K. Sunat")</li>
 *   <li>Thai names and maiden/compound surnames</li>
 * </ul>
 */
public class FacultyNameResolver {

    private static final Set<String> TITLES_TO_STRIP = Set.of(
            "dr", "prof", "professor", "asst", "assist", "assistant",
            "assoc", "associate", "mr", "mrs", "ms", "miss", "phd", "lecturer"
    );

    /**
     * Holds parsed components and variants of a faculty member's name.
     */
    public record FacultyNames(
            String givenNameEn,
            String middleNameEn,
            String familyNameEn,
            String givenNameTh,
            String familyNameTh,
            List<String> searchQueries,
            Set<String> normalizedMatchTokens
    ) {}

    private FacultyNameResolver() {
        // utility
    }

    /**
     * Resolves and extracts all realistic variants for a faculty member.
     */
    public static FacultyNames resolve(FsFaculty faculty) {
        if (faculty == null) {
            return new FacultyNames(null, null, null, null, null, List.of(), Set.of());
        }

        String rawEn = faculty.getNameEn();
        String givenEn = null;
        String middleEn = null;
        String familyEn = null;

        if (rawEn != null && !rawEn.isBlank()) {
            EnglishNameSplitter.Parts parts = EnglishNameSplitter.split(rawEn);
            givenEn = cleanToken(parts.firstName());
            String rawLast = cleanToken(parts.lastName());

            if (rawLast != null) {
                String[] lastTokens = rawLast.split("\\s+");
                if (lastTokens.length > 1) {
                    // e.g. "Runapongsa Saikaew" -> middle = Runapongsa, family = Saikaew
                    middleEn = lastTokens[0];
                    familyEn = String.join(" ", java.util.Arrays.copyOfRange(lastTokens, 1, lastTokens.length));
                } else {
                    familyEn = rawLast;
                }
            }
        }

        String givenTh = cleanToken(faculty.getFirstName());
        String familyTh = cleanToken(faculty.getLastName());

        Set<String> queries = new LinkedHashSet<>();
        Set<String> tokens = new HashSet<>();

        // 1. English queries & tokens
        if (givenEn != null && familyEn != null) {
            // First + Last (highest priority for modern publications)
            String firstLast = givenEn + " " + familyEn;
            queries.add(firstLast);
            addTokenPermutations(tokens, givenEn, familyEn);

            // First + Middle + Last (official directory full name)
            if (middleEn != null && !middleEn.isBlank()) {
                String full = givenEn + " " + middleEn + " " + familyEn;
                queries.add(full);
                addTokenPermutations(tokens, givenEn, middleEn + " " + familyEn);
                addTokenPermutations(tokens, givenEn + " " + middleEn, familyEn); // Saikaew, Kanda Runapongsa
                addTokenPermutations(tokens, givenEn, middleEn); // maiden name e.g. Kanda Runapongsa

                // Middle initial: e.g. "Kanda R. Saikaew"
                String midInit = middleEn.substring(0, 1);
                addTokenPermutations(tokens, givenEn + " " + midInit, familyEn);
                addTokenPermutations(tokens, givenEn, midInit + " " + familyEn);
            }

            // Hyphen variations: e.g. "Chakchai So-In" vs "Chakchai Soin"
            if (familyEn.contains("-")) {
                String unhyphenatedLast = familyEn.replace("-", "");
                queries.add(givenEn + " " + unhyphenatedLast);
                addTokenPermutations(tokens, givenEn, unhyphenatedLast);
            } else if (familyEn.contains(" ")) {
                String hyphenatedLast = familyEn.replace(" ", "-");
                addTokenPermutations(tokens, givenEn, hyphenatedLast);
            }

            // FirstInitial + Last: e.g. "K. Saikaew", "C. So-In"
            String init = givenEn.substring(0, 1);
            tokens.add(normalize(init + " " + familyEn));
            tokens.add(normalize(familyEn + " " + init));
        } else if (rawEn != null && !rawEn.isBlank()) {
            queries.add(rawEn.trim());
            tokens.add(normalize(rawEn));
        }

        // 2. Thai queries & tokens
        if (givenTh != null && familyTh != null) {
            String thaiFull = givenTh + " " + familyTh;
            queries.add(thaiFull);
            tokens.add(normalize(thaiFull));
            tokens.add(normalize(familyTh + " " + givenTh));

            // If compound Thai surname (e.g. รุณณาพงษ์ศา สายแก้ว)
            String[] thTokens = familyTh.split("\\s+");
            if (thTokens.length > 1) {
                String primarySurname = thTokens[thTokens.length - 1]; // สายแก้ว
                queries.add(givenTh + " " + primarySurname);
                tokens.add(normalize(givenTh + " " + primarySurname));
                tokens.add(normalize(primarySurname + " " + givenTh));
            }
        }

        return new FacultyNames(
                givenEn,
                middleEn,
                familyEn,
                givenTh,
                familyTh,
                new ArrayList<>(queries),
                Collections.unmodifiableSet(tokens)
        );
    }

    /**
     * Generates all prioritized search query strings to send to external APIs.
     */
    public static List<String> generateSearchQueries(FsFaculty faculty) {
        return resolve(faculty).searchQueries();
    }

    /**
     * Generates English-only query strings for international databases (Crossref, OpenAlex, DBLP).
     */
    public static List<String> generateEnglishQueries(FsFaculty faculty) {
        return generateSearchQueries(faculty).stream()
                .filter(q -> !q.matches(".*[\\p{InThai}].*"))
                .toList();
    }

    /**
     * Checks if a paper author string matches the given faculty member with high precision.
     *
     * @param paperAuthor the author name from publication metadata (e.g. "Saikaew, Kanda", "Kanda Saikaew", "So-In, C.")
     * @param faculty the faculty member record
     * @return true if paperAuthor refers to this faculty member
     */
    public static boolean matchesAuthor(String paperAuthor, FsFaculty faculty) {
        if (paperAuthor == null || paperAuthor.isBlank() || faculty == null) {
            return false;
        }

        FacultyNames names = resolve(faculty);
        String normAuthor = normalize(paperAuthor);
        if (normAuthor.isEmpty()) {
            return false;
        }

        // 1. Direct match with any precomputed normalized variant token
        if (names.normalizedMatchTokens().contains(normAuthor)) {
            return true;
        }

        // 2. Substring match against full canonical tokens
        for (String tok : names.normalizedMatchTokens()) {
            if (tok.length() >= 6 && (normAuthor.equals(tok) || normAuthor.contains(tok) || tok.contains(normAuthor))) {
                // Safeguard against short surname accidental substring collisions
                if (Math.abs(normAuthor.length() - tok.length()) <= 4) {
                    return true;
                }
            }
        }

        // 3. Token-based analysis for inverted "Last, First" or "Last First" formats
        String cleaned = stripTitles(paperAuthor);
        if (cleaned.contains(",")) {
            String[] parts = cleaned.split(",", 2);
            String part1 = cleanToken(parts[0]);
            String part2 = cleanToken(parts[1]);
            if (part1 != null && part2 != null) {
                // Inverted: part1 is Last, part2 is First
                if (matchesFamilyAndGiven(part1, part2, names)) {
                    return true;
                }
                // Non-inverted with comma
                if (matchesFamilyAndGiven(part2, part1, names)) {
                    return true;
                }
            }
        }

        // 4. Space-separated token matching
        String[] tokens = cleaned.split("[\\s\\-]+");
        if (tokens.length >= 2) {
            String firstTok = cleanToken(tokens[0]);
            String lastTok = cleanToken(tokens[tokens.length - 1]);
            if (matchesFamilyAndGiven(lastTok, firstTok, names) || matchesFamilyAndGiven(firstTok, lastTok, names)) {
                return true;
            }
        }

        return false;
    }

    private static boolean matchesFamilyAndGiven(String candidateFamily, String candidateGiven, FacultyNames names) {
        if (candidateFamily == null || candidateGiven == null) {
            return false;
        }

        String normCandFamily = normalize(candidateFamily);
        String normCandGiven = normalize(candidateGiven);

        if (normCandFamily.isEmpty() || normCandGiven.isEmpty()) {
            return false;
        }

        // Check English match
        if (names.familyNameEn() != null && names.givenNameEn() != null) {
            String normFam = normalize(names.familyNameEn());
            String normGiv = normalize(names.givenNameEn());
            String normFamNoHyphen = normFam.replace("-", "");

            boolean familyMatches = normCandFamily.equals(normFam)
                    || normCandFamily.equals(normFamNoHyphen)
                    || (names.middleNameEn() != null && normCandFamily.equals(normalize(names.middleNameEn())));

            if (familyMatches) {
                // Given name matches either full given name or initial, or candidate contains middle name (e.g. "Kanda Runapongsa")
                if (normCandGiven.equals(normGiv)
                        || (normCandGiven.length() == 1 && normGiv.startsWith(normCandGiven))
                        || normCandGiven.startsWith(normGiv)
                        || normGiv.startsWith(normCandGiven)) {
                    return true;
                }
            }
        }

        // Check Thai match
        if (names.familyNameTh() != null && names.givenNameTh() != null) {
            String normThFam = normalize(names.familyNameTh());
            String normThGiv = normalize(names.givenNameTh());

            if (normCandFamily.equals(normThFam) || normThFam.contains(normCandFamily)) {
                if (normCandGiven.equals(normThGiv) || normThGiv.contains(normCandGiven)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void addTokenPermutations(Set<String> tokens, String given, String family) {
        String normG = normalize(given);
        String normF = normalize(family);

        tokens.add(normG + normF);
        tokens.add(normF + normG);
    }

    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u0E00-\\u0E7F]", "");
    }

    private static String cleanToken(String s) {
        if (s == null || s.isBlank()) return null;
        String t = stripTitles(s.trim());
        return t.isEmpty() ? null : t;
    }

    private static String stripTitles(String s) {
        String[] words = s.trim().split("\\s+");
        List<String> valid = new ArrayList<>();
        for (String w : words) {
            String cleaned = w.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\u0E00-\\u0E7F]", "");
            if (!TITLES_TO_STRIP.contains(cleaned) && !cleaned.isEmpty()) {
                valid.add(w);
            }
        }
        return String.join(" ", valid).trim();
    }
}

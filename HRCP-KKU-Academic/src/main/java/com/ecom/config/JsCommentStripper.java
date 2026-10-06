package com.ecom.config;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/**
 * Removes comments from JavaScript, leaving every other character as it was.
 *
 * <p>The source keeps its comments — many explain why a piece of code exists and
 * are worth keeping — but there is no reason to ship them: ZAP's "Suspicious
 * Comments" rule flags them on every page, and they describe internals to
 * anyone reading the page source. This runs on what is served instead: the
 * static scripts under {@code /js} ({@link CommentStrippingResourceTransformer})
 * and the inline {@code <script>} blocks of rendered pages
 * ({@link ScriptCommentDialect}).
 *
 * <p>It is a tokenizer, not a regex, because {@code //} turns up inside strings
 * and URLs and {@code /*} inside regular expressions, and both must survive.
 * Template literals are followed into their {@code ${...}} parts. A regular
 * expression literal is told apart from division by the token before it, the
 * same heuristic editors use. A block comment that spanned lines leaves a
 * newline behind, so automatic semicolon insertion sees what it saw before.
 */
public final class JsCommentStripper {

    /** Keywords after which a {@code /} starts a regular expression, not a division. */
    private static final Set<String> KEYWORDS_BEFORE_EXPRESSION = Set.of(
            "return", "typeof", "instanceof", "in", "of", "new", "delete", "void",
            "throw", "case", "do", "else", "yield", "await");

    private static final String PUNCTUATION_BEFORE_EXPRESSION = "(,=:[!&|?{};+-*%<>~^}";

    private final String src;
    private final StringBuilder out;
    private final int n;
    private int i;
    /** Open braces in code, so a {@code }} can be matched to the {@code ${} that opened it. */
    private int braceDepth;
    /** Brace depth at each {@code ${} of the template literals we are inside. */
    private final Deque<Integer> templateHoles = new ArrayDeque<>();

    private JsCommentStripper(String src) {
        this.src = src;
        this.n = src.length();
        this.out = new StringBuilder(n);
    }

    public static String strip(String src) {
        if (src == null || (src.indexOf("//") < 0 && src.indexOf("/*") < 0)) {
            return src;
        }
        JsCommentStripper s = new JsCommentStripper(src);
        s.run();
        return s.out.toString();
    }

    private void run() {
        while (i < n) {
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                // The line break stays (\r\n included): it may end a statement
                while (i < n && src.charAt(i) != '\n' && src.charAt(i) != '\r') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                int end = src.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                int newline = src.indexOf('\n', i);
                out.append(newline >= 0 && newline < stop ? '\n' : ' ');
                i = stop;
            } else if (c == '\'' || c == '"') {
                copyString(c);
            } else if (c == '`') {
                out.append(c);
                i++;
                copyTemplateBody();
            } else if (c == '/' && regexCanStartHere()) {
                copyRegex();
            } else if (c == '{') {
                braceDepth++;
                out.append(c);
                i++;
            } else if (c == '}') {
                braceDepth--;
                out.append(c);
                i++;
                if (!templateHoles.isEmpty() && braceDepth == templateHoles.peek()) {
                    templateHoles.pop();
                    copyTemplateBody();
                }
            } else {
                out.append(c);
                i++;
            }
        }
    }

    private void copyString(char quote) {
        out.append(quote);
        i++;
        while (i < n) {
            char c = src.charAt(i);
            out.append(c);
            i++;
            if (c == '\\' && i < n) {
                out.append(src.charAt(i));
                i++;
            } else if (c == quote || c == '\n') {
                return;
            }
        }
    }

    /** Copies template text up to the closing backtick, or up to a {@code ${} and back into code. */
    private void copyTemplateBody() {
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\\' && i + 1 < n) {
                out.append(c).append(src.charAt(i + 1));
                i += 2;
            } else if (c == '`') {
                out.append(c);
                i++;
                return;
            } else if (c == '$' && i + 1 < n && src.charAt(i + 1) == '{') {
                out.append("${");
                i += 2;
                templateHoles.push(braceDepth);
                braceDepth++;
                return;
            } else {
                out.append(c);
                i++;
            }
        }
    }

    private void copyRegex() {
        out.append('/');
        i++;
        boolean inClass = false;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\n') {
                return; // not a regex after all; carry on as code
            }
            out.append(c);
            i++;
            if (c == '\\' && i < n) {
                out.append(src.charAt(i));
                i++;
            } else if (c == '[') {
                inClass = true;
            } else if (c == ']') {
                inClass = false;
            } else if (c == '/' && !inClass) {
                return;
            }
        }
    }

    private boolean regexCanStartHere() {
        int k = out.length() - 1;
        while (k >= 0 && Character.isWhitespace(out.charAt(k))) {
            k--;
        }
        if (k < 0) {
            return true;
        }
        char last = out.charAt(k);
        if (PUNCTUATION_BEFORE_EXPRESSION.indexOf(last) >= 0) {
            return true;
        }
        if (Character.isJavaIdentifierPart(last)) {
            int start = k;
            while (start > 0 && Character.isJavaIdentifierPart(out.charAt(start - 1))) {
                start--;
            }
            return KEYWORDS_BEFORE_EXPRESSION.contains(out.substring(start, k + 1));
        }
        return false;
    }
}

package com.mcverse.jobify.common.text;

import java.util.regex.Pattern;

/**
 * Turns the sanitized HTML of a job description into the plain text a search should look at, so that a search for
 * {@code strong} does not match the tag {@code <strong>}. Descriptions are already reduced to a small set of safe tags
 * by the HTML sanitizer, so removing tags and decoding the few entities it can produce is enough.
 */
public final class PlainText {

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private PlainText() {}

    /** Tags become a space (so words in neighbouring paragraphs do not run together), entities are decoded. */
    public static String fromHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = TAG.matcher(html).replaceAll(" ");
        text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
                .replace("&amp;", "&"); // last, so "&amp;lt;" ends up as "&lt;" and not "<"
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }
}

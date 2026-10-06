package com.mcverse.jobify.common;

import com.mcverse.jobify.common.text.PlainText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Search looks at the words of a description, never at its markup. */
class PlainTextTest {

    @Test
    void tagsAreRemovedAndWordsInNeighbouringBlocksDoNotRunTogether() {
        assertEquals("Hello world", PlainText.fromHtml("<p><strong>Hello</strong></p><p>world</p>"));
        assertEquals("one two", PlainText.fromHtml("<ul><li>one</li><li>two</li></ul>"));
    }

    @Test
    void theNameOfATagIsNotText() {
        assertEquals("bold", PlainText.fromHtml("<strong>bold</strong>"));
    }

    @Test
    void entitiesAreDecodedAndAmpersandLast() {
        assertEquals("R&D <team> \"quoted\" it's", PlainText.fromHtml("R&amp;D &lt;team&gt; &quot;quoted&quot; it&#39;s"));
        assertEquals("&lt; stays", PlainText.fromHtml("&amp;lt; stays"));
    }

    @Test
    void whitespaceIsCollapsedAndTrimmed() {
        assertEquals("a b c", PlainText.fromHtml("  <p>a\n\n  b</p>\t<p>c</p>  "));
    }

    @Test
    void nothingGivesAnEmptyString() {
        assertEquals("", PlainText.fromHtml(null));
        assertEquals("", PlainText.fromHtml("   "));
        assertEquals("", PlainText.fromHtml("<p></p>"));
    }
}

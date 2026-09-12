package se.havochvatten.symphonyconfig.setup;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfirmImportTest {

    private String layoutOf(String message) {
        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured));
            ConfirmImport.layoutMessage(message);
        } finally {
            System.setOut(previous);
        }
        return captured.toString();
    }

    @Test
    void keepsTextFollowingAnOverLongWord() {
        String longWord = "x".repeat(85);
        String laidOut = layoutOf("Pending matrix import: \"" + longWord + "\" awaiting confirmation");

        assertTrue(laidOut.contains("Pending matrix import:"),
            "The headline must survive. Output was:\n" + laidOut);
        assertTrue(laidOut.contains("awaiting confirmation"),
            "Everything after an over-long word must survive: an operator-supplied matrix or "
                + "baseline name must never truncate the description of what is being confirmed. "
                + "Output was:\n" + laidOut);
    }

    @Test
    void breaksAnOverLongWordRatherThanOverflowing() {
        String laidOut = layoutOf("y".repeat(200));

        for (String line : laidOut.split("\n")) {
            if (line.startsWith("-")) {
                continue; // the separator rule
            }
            assertTrue(line.length() <= 80,
                "No laid-out line may exceed the width. Offending line was " + line.length()
                    + " characters:\n" + line);
        }
        assertEquals(200, laidOut.replace("\n", "").chars().filter(c -> c == 'y').count(),
            "Every character of an over-long word must be emitted, not truncated");
    }

    @Test
    void wrapsOnWordBoundariesAndKeepsBlankLines() {
        // Long enough to exceed LINE_WIDTH (80) and force a wrap; chosen so the break falls
        // cleanly between two words rather than requiring a hard mid-word split.
        String longSecondParagraph = "second paragraph continues with enough additional words "
            + "to exceed the eighty character line width limit and force a wrap onto a third line";
        String laidOut = layoutOf("first paragraph\n\n" + longSecondParagraph);

        assertTrue(laidOut.contains(
            "first paragraph\n\nsecond paragraph continues with enough additional words to exceed the eighty"),
            "A blank line between paragraphs must be preserved, and the paragraph following it "
                + "must start on its own line. Output was:\n" + laidOut);
        assertTrue(laidOut.contains(
            "second paragraph continues with enough additional words to exceed the eighty\n"
                + "character line width limit and force a wrap onto a third line"),
            "A paragraph longer than the line width must wrap at the word boundary before it, "
                + "not mid-word. Output was:\n" + laidOut);
    }
}

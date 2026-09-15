package se.havochvatten.symphonyconfig.setup;

import se.havochvatten.symphonyconfig.setup.database.ReplacementImpact;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ConfirmImport {
    private static final String PROCEED_WITH_THE_IMPORT = "Proceed with the import?";

    public static boolean confirmToProceed(String message, String abortMessage) {
        return confirmToProceed(message, abortMessage, null, null);
    }

    /**
     * Confirms an action after printing {@code information} above the prompt. Distinct from
     * {@link #confirmToProceed(String, String)}, whose second argument is the message printed
     * when the operator declines: three consecutive nullable Strings in the full signature made
     * the two slots easy to confuse, and a notice delivered after a refusal helps nobody.
     */
    public static boolean confirmToProceedWithInformation(String message, @Nullable String information) {
        return confirmToProceed(message, null, information, null);
    }

    private static final int LINE_WIDTH = 80;

    /**
     * Prints a message wrapped at {@link #LINE_WIDTH} columns, above a separator rule. Blank
     * lines between paragraphs are preserved.
     * <p>
     * A word longer than the line width is broken rather than dropped. The regex this replaced
     * anchored every alternative with \G and had no branch matching an over-long run of
     * non-whitespace, so the matcher simply stopped there and everything after it was lost
     * silently, including the subject of the confirmation the operator was being asked for.
     */
    public static void layoutMessage(String message) {
        StringBuilder justified = new StringBuilder();

        for (String paragraph : message.split("\n", -1)) {
            String remaining = paragraph.strip();

            if (remaining.isEmpty()) {
                justified.append('\n');
                continue;
            }

            while (!remaining.isEmpty()) {
                int breakAt = breakPosition(remaining);
                justified.append(remaining, 0, breakAt).append('\n');
                remaining = remaining.substring(breakAt).stripLeading();
            }
        }

        System.out.println(justified);
        System.out.println("-".repeat(Math.min(Math.max(message.length(), 1), LINE_WIDTH)));
    }

    /** Where to break: the last space within the width, or a hard break through a long word. */
    private static int breakPosition(String text) {
        if (text.length() <= LINE_WIDTH) {
            return text.length();
        }

        int lastSpace = text.lastIndexOf(' ', LINE_WIDTH);
        return lastSpace > 0 ? lastSpace : LINE_WIDTH;
    }

    /**
     * @param message      the headline, wrapped and printed above a separator rule
     * @param abortMessage printed only when the operator declines; may be null
     * @param information  printed after the headline and before the prompt; may be null
     * @param userPrompt   overrides the default "Proceed with the import?"; may be null
     */
    public static boolean confirmToProceed(String message, @Nullable String abortMessage, @Nullable String information, @Nullable String userPrompt) {
        Scanner prompt = new Scanner(System.in);
        layoutMessage(message);
        if (information != null) {
            System.out.println(information);
        }
        System.out.printf("%n%s ('y' to confirm)%n", userPrompt == null ? PROCEED_WITH_THE_IMPORT : userPrompt);
        System.out.print("> ");

        if (!prompt.nextLine().trim().equalsIgnoreCase("y")) {
            if (abortMessage != null) {
                System.out.println(abortMessage);
            }
            return false;
        }

        System.out.println();
        return true;
    }

    private static final String SENSITIVITY_MATRICES_WARNING =
        "WARNING. Update mode 'replace' permanently removes all sensitivity matrices, including "
        + "any user-created matrices.\n"
        + "The specified baseline version is coupled to matrices owned by the following "
        + "users (by username): %s";

    private static final String RELIABILITY_PARTITION_WARNING =
        "WARNING. The specified baseline version is coupled to %d reliability partition "
        + "polygon(s). Because the tool was invoked in 'replace' mode, proceeding with the "
        + "import procedure will permanently delete the associated partition(s).\n"
        + "Please note that at present this tool does not include functionality to import "
        + "reliability partition polygons; which effectively means that if that feature is "
        + "desired for this baseline version and you choose to proceed at this point, you "
        + "will need to insert them manually.";

    private static final String REPLACEMENT_SUMMARY =
        "WARNING. Update mode 'replace' will permanently delete the following existing data "
        + "on baseline version %s before importing:\n%s";

    /** Package-private so the wording of each line can be asserted without driving stdin. */
    static List<String> impactLines(ReplacementImpact impact) {
        List<String> lines = new ArrayList<>();

        if (impact.metadataBands() > 0 || impact.metadataValues() > 0) {
            lines.add(String.format("- band metadata: %d band(s) carrying %d translated value(s)",
                impact.metadataBands(), impact.metadataValues()));
        }
        if (impact.sensitivityMatrices() > 0) {
            lines.add(String.format(
                "- sensitivity matrices: %d (every sensitivity score they hold goes with them)",
                impact.sensitivityMatrices()));
        }
        if (impact.calculationAreas() > 0) {
            // The foreign clause is stated only when it applies: an area another baseline
            // version owns is in scope through its link to a matrix here, which is the one
            // part of the delete set the option name does not suggest.
            lines.add(String.format("- calculation areas: %d (with %d area polygon(s))%s",
                impact.calculationAreas(), impact.calculationAreaPolygons(),
                impact.foreignCalculationAreas() > 0
                    ? String.format(", %d of them belonging to another baseline version",
                        impact.foreignCalculationAreas())
                    : ""));
        }
        if (impact.reliabilityPartitions() > 0) {
            lines.add(String.format("- reliability partition polygons: %d",
                impact.reliabilityPartitions()));
        }

        return lines;
    }

    /**
     * Confirms a destructive replace. Fires whenever the replace would delete anything at all,
     * not only when a matrix is user-owned or a reliability partition exists: on an ordinary
     * operator-managed baseline neither of those holds, and the replace is destructive anyway.
     *
     * @param baselineLabel how to name the target baseline version in the warning
     * @param impact        what the replace will delete, counted beforehand
     * @return true to proceed
     */
    public static boolean confirmToProceedWithReplacement(String baselineLabel, ReplacementImpact impact) {
        if (impact.isEmpty()) {
            return true;
        }

        List<String> parts = new ArrayList<>();
        parts.add(String.format(REPLACEMENT_SUMMARY, baselineLabel,
            String.join("\n", impactLines(impact))));

        if (!impact.matrixOwners().isEmpty()) {
            parts.add(String.format(SENSITIVITY_MATRICES_WARNING,
                String.join(", ", impact.matrixOwners())));
        }
        if (impact.reliabilityPartitions() > 0) {
            parts.add(String.format(RELIABILITY_PARTITION_WARNING, impact.reliabilityPartitions()));
        }

        return confirmToProceed(String.join("\n\n", parts),
            "Replace procedure aborted interactively.",
            null, "Confirm deletion of the data listed above to proceed.");
    }

    private ConfirmImport() {}
}

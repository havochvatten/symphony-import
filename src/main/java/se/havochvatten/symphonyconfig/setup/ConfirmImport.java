package se.havochvatten.symphonyconfig.setup;

import se.havochvatten.symphonyconfig.setup.database.ReplacementImpact;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfirmImport {
    private static final String PROCEED_WITH_THE_IMPORT = "Proceed with the import?";

    public static boolean confirmToProceed(String message, String abortMessage) {
        return confirmToProceed(message, abortMessage, null, null);
    }

    private static final Pattern splitWidthExpression = Pattern.compile("\\G(\\n{2,})|\\G(\\n)|\\G[ \\t]*(.{1,80}(?=\\s|$))");

    public static void layoutMessage(String message) {
        Matcher lineMatcher = splitWidthExpression.matcher(message);
        StringBuilder justified = new StringBuilder();
        while (lineMatcher.find()) {
            if (lineMatcher.group(1) != null) {
                justified.append('\n');
            }
            if (lineMatcher.group(3) != null) {
                justified.append(lineMatcher.group(3)).append('\n');
            }
        }
        System.out.println(justified);
        System.out.println("-".repeat(Math.min(message.length(), 80)));
    }

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

    private static List<String> impactLines(ReplacementImpact impact) {
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
            lines.add(String.format("- calculation areas: %d (with %d area polygon(s))",
                impact.calculationAreas(), impact.calculationAreaPolygons()));
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

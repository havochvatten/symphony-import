package se.havochvatten.symphonyconfig.setup;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    public static boolean confirmToProceedWithReplacement(List<String> matrixOwnersList, int reliabilityCount) {
        if (matrixOwnersList.size() + reliabilityCount > 0) {
            String[] warningParts = new String[]{
                !matrixOwnersList.isEmpty() ?
                    String.format(SENSITIVITY_MATRICES_WARNING, String.join(", ", matrixOwnersList))
                    : null,
                reliabilityCount > 0 ?
                    String.format(RELIABILITY_PARTITION_WARNING, reliabilityCount)
                    : null,
            };

            return confirmToProceed(Arrays.stream(warningParts)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n\n")),
                "Replace procedure aborted interactively.",
                null, "Confirm deletion of auxiliary resources to proceed.");
        }

        return true;
    }

    private ConfirmImport() {}
}

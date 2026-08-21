package se.havochvatten.symphonyconfig.setup;

import java.util.Scanner;

public class ConfirmImport {
    public static boolean confirmToProceed(String message, String abortMessage) {
        Scanner prompt = new Scanner(System.in);
        System.out.println(message);
        System.out.println("-".repeat(message.length()));
        System.out.println("\nProceed with the import? ('y' to confirm)");
        System.out.print("> ");

        if (!prompt.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println(abortMessage);
            return false;
        }
        return true;
    }

    private ConfirmImport() {}
}

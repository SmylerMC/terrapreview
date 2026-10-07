package net.smyler.terrapreview;

/**
 * Entry point of the TerraPreview application.
 */
public final class TerraPreview {

    private TerraPreview() {}

    /**
     * The greeting message printed by this application.
     */
    public static final String GREETING = "Hello, TerraPreview!";

    /**
     * Returns the greeting message of this application.
     *
     * @return the greeting message
     */
    public static String greeting() {
        return GREETING;
    }

    /**
     * Application entry point.
     *
     * @param args command line arguments, ignored
     */
    public static void main(String[] args) {
        System.out.println(greeting());
    }
}

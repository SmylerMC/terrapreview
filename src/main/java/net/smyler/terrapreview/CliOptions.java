package net.smyler.terrapreview;

import java.util.NoSuchElementException;

/**
 * The command line options of Terrapreview.
 *
 * @param port the port the web server listens on
 * @param projection the name of the map projection to use
 */
public record CliOptions(int port, String projection) {
    /**
     * The default options, used when no flag is given.
     */
    public static final CliOptions DEFAULT = new CliOptions(PreviewWebServer.DEFAULT_PORT, Projections.BTE);

    private static final String PORT_FLAG = "--port";
    private static final String PROJECTION_FLAG = "--projection";
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    /**
     * Parses the command line arguments into options.
     *
     * <p>Recognized flags: {@code --port <port>} (or {@code -p}, {@code --port=<port>})
     * and {@code --projection <name>} (or {@code -P}, {@code --projection=<name>}).
     *
     * @param args the command line arguments
     * @return the parsed options, with defaults for absent flags
     * @throws NoSuchElementException when a flag is given without a value
     * @throws IllegalArgumentException when a value is invalid or an unknown flag is given
     */
    public static CliOptions parse(String[] args) {
        int port = DEFAULT.port();
        String projection = DEFAULT.projection();
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if (PORT_FLAG.equals(arg) || "-p".equals(arg)) {
                i += 1;
                port = parsePortValue(valueOf(args, arg, i));
            } else if (arg.startsWith(PORT_FLAG + '=')) {
                port = parsePortValue(arg.substring(PORT_FLAG.length() + 1));
            } else if (PROJECTION_FLAG.equals(arg) || "-P".equals(arg)) {
                i += 1;
                projection = valueOf(args, arg, i);
            } else if (arg.startsWith(PROJECTION_FLAG + '=')) {
                projection = arg.substring(PROJECTION_FLAG.length() + 1);
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
            i += 1;
        }
        return new CliOptions(port, projection);
    }

    private static String valueOf(String[] args, String flag, int valueIndex) {
        if (valueIndex >= args.length) {
            throw new NoSuchElementException("Missing value for " + flag);
        }
        return args[valueIndex];
    }

    private static int parsePortValue(String value) {
        int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("Invalid port: " + value);
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        return port;
    }
}

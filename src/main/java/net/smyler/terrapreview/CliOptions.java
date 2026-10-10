package net.smyler.terrapreview;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The command line options of Terrapreview.
 *
 * @param port the port the web server listens on
 * @param projection the name of the map projection to use
 * @param mcVersion the version of the Minecraft client to load block textures from,
 *                  or null for the latest release
 * @param centerLongitude the longitude of the initial map center, in degrees, or null for the default
 * @param centerLatitude the latitude of the initial map center, in degrees, or null for the default
 * @param centerX the Minecraft block x coordinate of the initial map center, or null for the default
 * @param centerZ the Minecraft block z coordinate of the initial map center, or null for the default
 * @param openBrowser whether to open the user's browser at the preview page when the server starts,
 *                    or null to open it only when stdout is a terminal
 */
public record CliOptions(
        int port,
        String projection,
        String mcVersion,
        Double centerLongitude,
        Double centerLatitude,
        Double centerX,
        Double centerZ,
        Boolean openBrowser) {
    /**
     * The default options, used when no flag is given.
     */
    public static final CliOptions DEFAULT =
            new CliOptions(PreviewWebServer.DEFAULT_PORT, Projections.BTE, null, null, null, null, null, null);

    private static final String PORT_FLAG = "--port";
    private static final String PORT_SHORT_FLAG = "-p";
    private static final String PROJECTION_FLAG = "--projection";
    private static final String PROJECTION_SHORT_FLAG = "-P";
    private static final String MC_VERSION_FLAG = "--mc-version";
    private static final String CENTER_LON_FLAG = "--center-lon";
    private static final String CENTER_LAT_FLAG = "--center-lat";
    private static final String CENTER_X_FLAG = "--center-x";
    private static final String CENTER_Z_FLAG = "--center-z";
    private static final String OPEN_BROWSER_FLAG = "--open-browser";
    private static final String NO_OPEN_BROWSER_FLAG = "--no-open-browser";

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final double MIN_LONGITUDE = -180.0D;
    private static final double MAX_LONGITUDE = 180.0D;
    private static final double MIN_LATITUDE = -90.0D;
    private static final double MAX_LATITUDE = 90.0D;

    /**
     * Parses the command line arguments into options.
     *
     * <p>Every flag accepts its value either as the next argument or after an equals sign,
     * as in {@code --port=9090}. The initial map center can be given either in geographic
     * coordinates with {@code --center-lon} and {@code --center-lat}, or in Minecraft block
     * coordinates with {@code --center-x} and {@code --center-z}, but not both.
     *
     * @param args the command line arguments
     * @return the parsed options, with defaults for absent flags
     * @throws NoSuchElementException when a flag is given without a value
     * @throws IllegalArgumentException when a value is invalid, a center is incomplete or an unknown flag is given
     */
    public static CliOptions parse(String[] args) {
        int port = DEFAULT.port();
        String projection = DEFAULT.projection();
        String mcVersion = null;
        Double centerLongitude = null;
        Double centerLatitude = null;
        Double centerX = null;
        Double centerZ = null;
        Boolean openBrowser = null;
        List<String> arguments = normalize(args);
        int i = 0;
        while (i < arguments.size()) {
            String flag = arguments.get(i);
            if (!isKnownFlag(flag)) {
                throw new IllegalArgumentException("Unknown argument: " + flag);
            }
            i += 1;
            switch (flag) {
                case OPEN_BROWSER_FLAG -> openBrowser = true;
                case NO_OPEN_BROWSER_FLAG -> openBrowser = false;
                default -> {
                    String value = valueOf(arguments, flag, i);
                    i += 1;
                    switch (flag) {
                        case PORT_FLAG, PORT_SHORT_FLAG -> port = parsePortValue(value);
                        case PROJECTION_FLAG, PROJECTION_SHORT_FLAG -> projection = value;
                        case MC_VERSION_FLAG -> mcVersion = value;
                        case CENTER_LON_FLAG -> centerLongitude = parseLongitude(value);
                        case CENTER_LAT_FLAG -> centerLatitude = parseLatitude(value);
                        case CENTER_X_FLAG -> centerX = parseCoordinate(value);
                        case CENTER_Z_FLAG -> centerZ = parseCoordinate(value);
                        case OPEN_BROWSER_FLAG, NO_OPEN_BROWSER_FLAG ->
                            throw new IllegalArgumentException("The browser flags do not take a value: " + flag);
                        default -> throw new IllegalArgumentException("Unknown argument: " + flag);
                    }
                }
            }
        }
        if ((centerLongitude == null) != (centerLatitude == null)) {
            throw new IllegalArgumentException(CENTER_LON_FLAG + " and " + CENTER_LAT_FLAG + " must be given together");
        }
        if ((centerX == null) != (centerZ == null)) {
            throw new IllegalArgumentException(CENTER_X_FLAG + " and " + CENTER_Z_FLAG + " must be given together");
        }
        if (centerLongitude != null && centerX != null) {
            throw new IllegalArgumentException(
                    "The center can be given either in geographic or in Minecraft coordinates, not both");
        }
        return new CliOptions(
                port, projection, mcVersion, centerLongitude, centerLatitude, centerX, centerZ, openBrowser);
    }

    private static boolean isKnownFlag(String flag) {
        return switch (flag) {
            case PORT_FLAG,
                    PORT_SHORT_FLAG,
                    PROJECTION_FLAG,
                    PROJECTION_SHORT_FLAG,
                    MC_VERSION_FLAG,
                    CENTER_LON_FLAG,
                    CENTER_LAT_FLAG,
                    CENTER_X_FLAG,
                    CENTER_Z_FLAG,
                    OPEN_BROWSER_FLAG,
                    NO_OPEN_BROWSER_FLAG -> true;
            default -> false;
        };
    }

    private static List<String> normalize(String[] args) {
        List<String> arguments = new ArrayList<>(args.length);
        for (String arg : args) {
            int separator = arg.indexOf('=');
            if (arg.startsWith("--") && separator > 0) {
                arguments.add(arg.substring(0, separator));
                arguments.add(arg.substring(separator + 1));
            } else {
                arguments.add(arg);
            }
        }
        return arguments;
    }

    private static String valueOf(List<String> arguments, String flag, int valueIndex) {
        if (valueIndex >= arguments.size()) {
            throw new NoSuchElementException("Missing value for " + flag);
        }
        return arguments.get(valueIndex);
    }

    private static int parsePortValue(String value) {
        int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port: " + value);
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        return port;
    }

    private static double parseLongitude(String value) {
        double longitude = parseCoordinate(value);
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new IllegalArgumentException("Longitude out of range: " + longitude);
        }
        return longitude;
    }

    private static double parseLatitude(String value) {
        double latitude = parseCoordinate(value);
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            throw new IllegalArgumentException("Latitude out of range: " + latitude);
        }
        return latitude;
    }

    private static double parseCoordinate(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid coordinate: " + value);
        }
    }
}

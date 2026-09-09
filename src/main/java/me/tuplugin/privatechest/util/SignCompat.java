package me.tuplugin.privatechest.util;

import org.bukkit.Material;
import org.bukkit.block.Sign;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads sign text across Minecraft versions.
 *
 * <p>Signs gained a second writable side in Minecraft 1.20. {@code Sign#getLines()} still exists but
 * only ever returns the front, so a plugin that reads it cannot see text on the back. That is how a
 * {@code [Private]} tag written on the back of a sign went unnoticed in 2.x: the sign locked nothing,
 * and breaking such a sign did not release the container it appeared to protect.</p>
 *
 * <p>Both sides are read where the API supports it, resolved by reflection so the plugin still
 * compiles and runs against older versions.</p>
 *
 * @since 3.0
 */
public final class SignCompat {

    private static final boolean DUAL_SIDED;

    private static Method getSide;
    private static Object frontSide;
    private static Object backSide;
    private static Method signSideGetLines;

    static {
        boolean dualSided = false;
        try {
            Class<?> sideEnum = Class.forName("org.bukkit.block.sign.Side");
            Class<?> signSideClass = Class.forName("org.bukkit.block.sign.SignSide");

            getSide = Sign.class.getMethod("getSide", sideEnum);
            signSideGetLines = signSideClass.getMethod("getLines");

            Object[] sides = sideEnum.getEnumConstants();
            for (Object side : sides) {
                String name = ((Enum<?>) side).name();
                if ("FRONT".equals(name)) {
                    frontSide = side;
                } else if ("BACK".equals(name)) {
                    backSide = side;
                }
            }

            dualSided = frontSide != null && backSide != null;
        } catch (Exception e) {
            // Pre-1.20 server: signs only have one side, and getLines() is the whole story.
            dualSided = false;
        }
        DUAL_SIDED = dualSided;
    }

    private SignCompat() {
        // Utility class.
    }

    /**
     * Whether this server's signs have two writable sides.
     */
    public static boolean isDualSided() {
        return DUAL_SIDED;
    }

    /**
     * Every line of text on a sign, from both sides where they exist.
     */
    public static List<String> getAllLines(Sign sign) {
        List<String> lines = new ArrayList<>(8);
        if (sign == null) {
            return lines;
        }

        if (DUAL_SIDED) {
            addSideLines(lines, sign, frontSide);
            addSideLines(lines, sign, backSide);
            if (!lines.isEmpty()) {
                return lines;
            }
            // Reflection failed at runtime; fall through to the single-sided path.
        }

        String[] frontLines = sign.getLines();
        if (frontLines != null) {
            lines.addAll(Arrays.asList(frontLines));
        }
        return lines;
    }

    private static void addSideLines(List<String> target, Sign sign, Object side) {
        try {
            Object signSide = getSide.invoke(sign, side);
            String[] lines = (String[]) signSideGetLines.invoke(signSide);
            if (lines != null) {
                target.addAll(Arrays.asList(lines));
            }
        } catch (Exception e) {
            // Handled by the caller falling back to getLines().
        }
    }

    /**
     * Whether any line on any side equals the given tag, ignoring case, colour codes and surrounding
     * whitespace.
     */
    public static boolean hasTag(Sign sign, String tag) {
        if (sign == null || tag == null) {
            return false;
        }

        String expected = stripFormatting(tag);
        for (String line : getAllLines(sign)) {
            if (expected.equals(stripFormatting(line))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a material is any kind of sign, including wall signs and hanging signs.
     */
    public static boolean isSign(Material material) {
        return material != null && material.name().endsWith("SIGN");
    }

    /**
     * Removes colour codes and normalises a line for comparison.
     */
    public static String stripFormatting(String line) {
        if (line == null) {
            return "";
        }

        StringBuilder result = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            // Skip a colour code and the character it applies to.
            if ((c == '\u00A7' || c == '&') && i + 1 < line.length()) {
                i++;
                continue;
            }
            result.append(c);
        }
        return result.toString().trim().toLowerCase();
    }
}

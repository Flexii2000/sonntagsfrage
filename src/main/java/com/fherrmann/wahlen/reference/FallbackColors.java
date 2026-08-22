package com.fherrmann.wahlen.reference;

import java.nio.charset.StandardCharsets;

/**
 * Farben fuer Parteien, die nicht in {@code parties.yaml} stehen — typischerweise
 * regionale Kleinparteien, die in genau einer Umfrage auftauchen.
 *
 * <p>Der Farbton wird deterministisch aus dem Kuerzel abgeleitet, damit dieselbe
 * Partei ueber Reimports und Deployments hinweg dieselbe Farbe behaelt. Saettigung
 * und Helligkeit sind fest, damit die Farbe zur restlichen Palette passt und den
 * Kontrast gegen beide Hintergruende haelt.
 */
final class FallbackColors {

    private FallbackColors() {
    }

    static String light(String key) {
        return hsl(hue(key), 0.55, 0.36);
    }

    static String dark(String key) {
        return hsl(hue(key), 0.60, 0.68);
    }

    private static int hue(String key) {
        int h = 0;
        for (byte b : String.valueOf(key).getBytes(StandardCharsets.UTF_8)) {
            h = h * 31 + b;
        }
        return Math.floorMod(h, 360);
    }

    private static String hsl(int hueDegrees, double saturation, double lightness) {
        double c = (1 - Math.abs(2 * lightness - 1)) * saturation;
        double x = c * (1 - Math.abs((hueDegrees / 60.0) % 2 - 1));
        double m = lightness - c / 2;
        double r;
        double g;
        double b;
        if (hueDegrees < 60) {
            r = c; g = x; b = 0;
        } else if (hueDegrees < 120) {
            r = x; g = c; b = 0;
        } else if (hueDegrees < 180) {
            r = 0; g = c; b = x;
        } else if (hueDegrees < 240) {
            r = 0; g = x; b = c;
        } else if (hueDegrees < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        return "#%02X%02X%02X".formatted(
                Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }
}

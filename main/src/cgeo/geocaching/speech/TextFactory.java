package cgeo.geocaching.speech;

import cgeo.geocaching.R;
import cgeo.geocaching.Settings;
import cgeo.geocaching.cgeoapplication;
import cgeo.geocaching.geopoint.Geopoint;
import cgeo.geocaching.geopoint.IConversion;
import cgeo.geocaching.utils.AngleUtils;

import java.util.Locale;

/**
 * Creates the output to be read by TTS.
 *
 */
public class TextFactory {
    public static String getText(Geopoint position, Geopoint target, float direction) {
        if (position == null || target == null) {
            return null;
        }
        return getDirection(position, target, direction) + ". " + getDistance(position, target);
    }

    private static String getDistance(Geopoint position, Geopoint target) {
        final float kilometers = position.distanceTo(target);

        if (Settings.isUseMetricUnits()) {
            return getDistance(kilometers, (int) (kilometers * 1000.0),
                    5.0f, 1.0f, 50,
                    R.plurals.tts_kilometers, R.string.tts_one_kilometer,
                    R.plurals.tts_meters, R.string.tts_one_meter);
        }
        return getDistance(kilometers / IConversion.MILES_TO_KILOMETER,
                (int) (kilometers * 1000.0 * IConversion.METERS_TO_FEET),
                3.0f, 0.2f, 300,
                R.plurals.tts_miles, R.string.tts_one_mile,
                R.plurals.tts_feet, R.string.tts_one_foot);
    }

    private static String getDistance(float farDistance, int nearDistance,
            float farFarAway, float farNearAway, int nearFarAway,
            int farId, int farOneId, int nearId, int nearOneId) {
        if (farDistance >= farFarAway) {
            // example: "5 kilometers" - always without decimal digits
            final int quantity = Math.round(farDistance);
            if (quantity == 1) {
                return getString(farOneId, quantity, String.valueOf(quantity));
            }
            return getQuantityString(farId, quantity, String.valueOf(quantity));
        }
        if (farDistance >= farNearAway) {
            // example: "2.2 kilometers" - decimals if necessary
            final float precision1 = Math.round(farDistance * 10.0f) / 10.0f;
            final float precision0 = Math.round(farDistance);
            if (Math.abs(precision1 - precision0) < 0.0001) {
                // this is an int - e.g. 2 kilometers
                final int quantity = (int) precision0;
                if (quantity == 1) {
                    return getString(farOneId, quantity, String.valueOf(quantity));
                }
                return getQuantityString(farId, quantity, String.valueOf(quantity));
            }
            // this is no int - e.g. 1.7 kilometers
            final String digits = String.format(Locale.getDefault(), "%.1f", farDistance);
            // always use the plural (9 leads to plural)
            return getQuantityString(farId, 9, digits);
        }
        // example: "34 meters"
        int quantity = nearDistance;
        if (quantity > nearFarAway) {
            // example: "120 meters" - rounded to 10 meters
            quantity = (int) Math.round(quantity / 10.0) * 10;
        }
        if (quantity == 1) {
            return getString(nearOneId, quantity, String.valueOf(quantity));
        }
        return getQuantityString(nearId, quantity, String.valueOf(quantity));
    }

    private static String getString(int resourceId, Object... formatArgs) {
        return cgeoapplication.getInstance().getString(resourceId, formatArgs);
    }

    private static String getQuantityString(int resourceId, int quantity, Object... formatArgs) {
        return cgeoapplication.getInstance().getResources().getQuantityString(resourceId, quantity, formatArgs);
    }

    private static String getDirection(Geopoint position, Geopoint target, float direction) {
        final int bearing = (int) position.bearingTo(target);
        final int degrees = (int) AngleUtils.normalize(bearing - direction);

        int hours = (degrees + 15) / 30;
        if (hours == 0) {
            hours = 12;
        }
        if (hours == 1) {
            return getString(R.string.tts_one_oclock, String.valueOf(hours));
        }
        return getString(R.string.tts_oclock, String.valueOf(hours));
    }
}

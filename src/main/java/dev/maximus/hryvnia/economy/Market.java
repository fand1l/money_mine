package dev.maximus.hryvnia.economy;

import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

/**
 * Daily market behavior. Prices are rolled deterministically from
 * (villager, shop index, real-world date), so a given villager quotes the
 * same price all day, different villagers quote different prices, and
 * everything re-rolls at midnight. The distribution is exponentially
 * weighted: the closer to the cheap end of the range, the rarer the price.
 */
public final class Market {
    private Market() {
    }

    /** Whether this villager takes card payments (some dodge taxes and want cash). */
    public static boolean acceptsCard(UUID villagerId) {
        int percent = Math.clamp(HryvniaConfig.INSTANCE.cardAcceptancePercent, 0, 100);
        return Math.floorMod(villagerId.hashCode(), 100) < percent;
    }

    public static long dayNumber() {
        return LocalDate.now().toEpochDay();
    }

    /** Today's date, used as the reset key for daily transfer limits. */
    public static String today() {
        return LocalDate.now().toString();
    }

    /**
     * Rolls today's price for a shop entry at a given villager. Falls back to
     * the flat {@code price} when no valid range is configured.
     */
    public static int rollPrice(HryvniaConfig.ShopEntry entry, UUID villagerId, int index) {
        int min = entry.priceMin;
        int max = entry.priceMax;
        if (min <= 0 || max < min) {
            return Math.max(1, entry.price);
        }
        if (max == min) {
            return min;
        }
        long seed = villagerId.getLeastSignificantBits()
                ^ (villagerId.getMostSignificantBits() * 31L)
                ^ (index * 0x9E3779B97F4A7C15L)
                ^ (dayNumber() * 0x517CC1B727220A95L);
        double u = new Random(seed).nextDouble();
        double k = Math.max(0.05, HryvniaConfig.INSTANCE.priceBias);
        // Inverse-CDF sample of a density proportional to e^(k*x) on [0,1]:
        // x is biased toward 1 (the expensive end), so cheap prices stay rare.
        double x = Math.log1p(u * Math.expm1(k)) / k;
        return min + (int) Math.round(x * (max - min));
    }

    /** Fee rounded up, so the bank never works for free on a non-zero rate. */
    public static long fee(long amount, double percent) {
        if (percent <= 0 || amount <= 0) {
            return 0;
        }
        return (long) Math.ceil(amount * percent / 100.0);
    }
}

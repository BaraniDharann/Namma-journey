package com.travelplatform.service;

import java.awt.image.BufferedImage;

/**
 * Does an image have the colours of an Indian Aadhaar or PAN card? A first filter that turns away
 * selfies, number plates and blank pages uploaded in place of the card.
 *
 * <p>It is only a filter: copying a card's colours is easy, so a "yes" here proves nothing. The
 * UIDAI signature on the Aadhaar QR ({@link AadhaarSecureQr}) is what tells a real card from a
 * forged one.
 *
 * <ul>
 *   <li>Aadhaar: the saffron band at the top and the green band at the bottom (both on the PVC card,
 *       the letter and the e-Aadhaar).</li>
 *   <li>PAN: the pale blue background printed across most of the card.</li>
 * </ul>
 *
 * Thresholds are deliberately low - a phone photo shows the card on a table, under a lamp - and
 * are checked against real cards in {@code SampleDocumentsTest}.
 */
public final class CardLook {

    /** Share of the pixels that must be saffron, and separately green, for an Aadhaar. */
    static final double AADHAAR_MIN_SHARE = 0.0015;
    /** Share of the pixels that must be the pale card blue for a PAN. */
    static final double PAN_MIN_SHARE = 0.12;
    /** Pixels sampled along each side at most; a card's colours show long before this. */
    private static final int MAX_SAMPLES_PER_SIDE = 400;

    private CardLook() {
    }

    public record Shares(double saffron, double green, double paleBlue) {
    }

    public static boolean looksLikeAadhaar(BufferedImage img) {
        Shares s = shares(img);
        return s.saffron() >= AADHAAR_MIN_SHARE && s.green() >= AADHAAR_MIN_SHARE;
    }

    public static boolean looksLikePan(BufferedImage img) {
        return shares(img).paleBlue() >= PAN_MIN_SHARE;
    }

    /**
     * What share of the image falls in each card colour, after trimming white margins (a card
     * scanned onto an A4 page is mostly empty page). Public for calibrating on real cards.
     */
    public static Shares shares(BufferedImage img) {
        return sharesOf(trimWhite(img));
    }

    /** The image cropped to the box around everything that is not near-white paper. */
    static BufferedImage trimWhite(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int stepX = Math.max(1, w / MAX_SAMPLES_PER_SIDE);
        int stepY = Math.max(1, h / MAX_SAMPLES_PER_SIDE);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
                boolean paper = Math.min(r, Math.min(g, b)) >= 235 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 12;
                if (!paper) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return img; // all paper
        }
        int cw = Math.min(w - minX, maxX - minX + stepX);
        int ch = Math.min(h - minY, maxY - minY + stepY);
        // Too small a box is a speck on blank paper, not a card: judge the whole page instead.
        return cw * 10 < w || ch * 10 < h ? img : img.getSubimage(minX, minY, cw, ch);
    }

    private static Shares sharesOf(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int stepX = Math.max(1, w / MAX_SAMPLES_PER_SIDE);
        int stepY = Math.max(1, h / MAX_SAMPLES_PER_SIDE);
        long total = 0, saffron = 0, green = 0, paleBlue = 0;
        float[] hsv = new float[3];
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int rgb = img.getRGB(x, y);
                java.awt.Color.RGBtoHSB((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, hsv);
                float hue = hsv[0] * 360f, sat = hsv[1], val = hsv[2];
                total++;
                if (hue >= 15 && hue <= 45 && sat >= 0.50f && val >= 0.55f) {
                    saffron++;
                } else if (hue >= 85 && hue <= 150 && sat >= 0.40f && val >= 0.30f) {
                    green++;
                } else if (hue >= 175 && hue <= 235 && sat >= 0.04f && sat <= 0.45f && val >= 0.55f) {
                    paleBlue++;
                }
            }
        }
        if (total == 0) {
            return new Shares(0, 0, 0);
        }
        return new Shares((double) saffron / total, (double) green / total, (double) paleBlue / total);
    }
}

package com.travelplatform.storage;

/**
 * How small a stored image may be made, by what it shows. A face only has to be recognisable on
 * a card; a licence or RC has to keep its fine print (numbers, dates, vehicle classes) readable
 * for verification and as evidence later, so documents get more pixels and more bytes.
 *
 * <p>{@code targetBytes} is a goal, not a hard limit: the compressor lowers JPEG quality, then
 * resolution down to {@code minSide}, and stops there rather than make a document unreadable.
 */
public enum ImageProfile {

    /** Driver profile photo / selfie. */
    PROFILE(512, 384, 50 * 1024),
    /** End-of-trip and vehicle photos: odometer digits and plates still readable. */
    PHOTO(1280, 960, 150 * 1024),
    /** Licence, RC, Aadhaar, PAN, registration certificate. */
    DOCUMENT(1600, 1280, 300 * 1024);

    final int maxSide;
    final int minSide;
    final int targetBytes;

    ImageProfile(int maxSide, int minSide, int targetBytes) {
        this.maxSide = maxSide;
        this.minSide = minSide;
        this.targetBytes = targetBytes;
    }

    public int targetBytes() {
        return targetBytes;
    }

    public int maxSide() {
        return maxSide;
    }
}

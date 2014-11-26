/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * put your documentation comment here
 */
public class ICCBased extends PColorSpace {

    public static final Name ICCBASED_KEY = new Name("ICCBased");
    public static final Name N_KEY = new Name("N");
    private static final Logger logger =
            Logger.getLogger(ICCBased.class.toString());
    // basic cache to speed up the lookup, can't be static as we handle
    // 3 and 4 band colours.
    private ConcurrentHashMap<Integer, Color> iccColorCache3B;
    private ConcurrentHashMap<Integer, Color> iccColorCache4B;
    private int numcomp;
    private PColorSpace alternate;
    private Stream stream;
    private ColorSpace colorSpace;
    // setting up an ICC colour look up is expensive, so if we get a failure
    // we just fallback to the alternative space to safe cpu time.
    private boolean failed;

    public ICCBased(Library l, Stream h) {
        super(l, h.getEntries());
        iccColorCache3B = new ConcurrentHashMap<Integer, Color>();
        iccColorCache4B = new ConcurrentHashMap<Integer, Color>();
        numcomp = h.getInt(N_KEY);
        switch (numcomp) {
            case 1:
                alternate = new DeviceGray(l, null);
                break;
            case 3:
                alternate = new DeviceRGB(l, null);
                break;
            case 4:
                alternate = new DeviceCMYK(l, null);
                break;
        }
        stream = h;
    }

    private static int generateKey(float[] f) {
        int key = (((int) (f[0] * 255) & 0xff) << 16) |
                (((int) (f[1] * 255) & 0xff) << 8) |
                (((int) (f[2] * 255) & 0xff) & 0xff);
        if (f.length == 4) key |= (((int) (f[3] * 255) & 0xff) << 24);
        return key;
    }

    private static Color addColorToCache(
            ConcurrentHashMap<Integer, Color> iccColorCache, int key,
            ColorSpace colorSpace, float[] f) {
        Color color = iccColorCache.get(key);
        if (color != null) {
            return color;
        } else {
            color = new Color(calculateColor(f, colorSpace));
            iccColorCache.put(key, color);
            return color;
        }
    }

    private static int calculateColor(float[] f, ColorSpace colorSpace) {
        int n = colorSpace.getNumComponents();
        // Get the reverse of f, and only take n values
        // Might as well limit the bounds while we're at it
        float[] fvalue = new float[n];
        int toCopy = n;
        int fLength = f.length;
        if (fLength < toCopy) {
            toCopy = fLength;
        }
        for (int i = 0; i < toCopy; i++) {
            float curr = f[fLength - 1 - i];
            if (curr < 0.0f) {
                curr = 0.0f;
            } else if (curr > 1.0f) {
                curr = 1.0f;
            }
            fvalue[i] = curr;
        }
        float[] frgbvalue = colorSpace.toRGB(fvalue);
        return (0xFF000000) |
                ((((int) (frgbvalue[0] * 255)) & 0xFF) << 16) |
                ((((int) (frgbvalue[1] * 255)) & 0xFF) << 8) |
                ((((int) (frgbvalue[2] * 255)) & 0xFF));
    }

    /**
     *
     */
    public synchronized void init() {
        if (inited) {
            return;
        }

        byte[] in;
        try {
            stream.init();
            in = stream.getDecodedStreamBytes(0);
            if (logger.isLoggable(Level.FINEST)) {
                String content = Utils.convertByteArrayToByteString(in);
                logger.finest("Content = " + content);
            }
            if (in != null) {
                ICC_Profile profile = ICC_Profile.getInstance(in);
                colorSpace = new ICC_ColorSpace(profile);
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Error Processing ICCBased Colour Profile", e);
        }
        inited = true;
    }

    /**
     * Get the alternative colour specified by the N dictionary entry.  DeviceGray,
     * DeviceRGB, or DeviceCMYK, depending on whether the value of N is  1, 3
     * or 4, respectively.
     *
     * @return PDF colour space represented by the N (number of components)key.
     */
    public PColorSpace getAlternate() {
        return alternate;
    }

    public Color getColor(float[] f, boolean fillAndStroke) {
        init();
        if (colorSpace != null && !failed) {
            try {
                // generate a key for the colour
                int key = generateKey(f);
                if (f.length <= 3) {
                    return addColorToCache(iccColorCache3B, key, colorSpace, f);
                } else {
                    return addColorToCache(iccColorCache4B, key, colorSpace, f);
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "Error getting ICCBased colour", e);
                failed = true;
            }
        }
        return alternate.getColor(f);
    }

    public ColorSpace getColorSpace() {
        return colorSpace;
    }

    /**
     * Gets the number of components specified by the N entry.
     *
     * @return number of colour components in color space
     */
    public int getNumComponents() {
        return numcomp;
    }
}

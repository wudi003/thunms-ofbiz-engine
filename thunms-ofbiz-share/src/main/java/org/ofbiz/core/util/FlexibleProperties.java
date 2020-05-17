/*
 * $Id: FlexibleProperties.java,v 1.1 2005/04/01 05:58:06 sfarquhar Exp $
 *
 *  Copyright (c) 2001 The Open For Business Project - www.ofbiz.org
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a
 *  copy of this software and associated documentation files (the "Software"),
 *  to deal in the Software without restriction, including without limitation
 *  the rights to use, copy, modify, merge, publish, distribute, sublicense,
 *  and/or sell copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included
 *  in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 *  OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 *  IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 *  CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
 *  OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 *  THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.ofbiz.core.util;


import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;


/**
 * Simple Class for flexibly working with properties files
 *
 * @author <a href="mailto:jonesde@ofbiz.org">David E. Jones</a>
 * @version 1.0
 */
public class FlexibleProperties extends Properties {

    private static final boolean truncateIfMissingDefault = false;
    private static final boolean doPropertyExpansionDefault = true;

    private URL url = null;
    private boolean doPropertyExpansion = doPropertyExpansionDefault;
    private boolean truncateIfMissing = truncateIfMissingDefault;

    // constructors
    public FlexibleProperties() {
        super();
    }

    public FlexibleProperties(Properties properties) {
        super(properties);
    }

    public FlexibleProperties(URL url) {
        this.url = url;
        init();
    }

    public FlexibleProperties(URL url, Properties properties) {
        super(properties);
        this.url = url;
        init();
    }

    // factories
    public static FlexibleProperties makeFlexibleProperties(Properties properties) {
        return new FlexibleProperties(properties);
    }

    public static FlexibleProperties makeFlexibleProperties(URL url) {
        return new FlexibleProperties(url);
    }

    public static FlexibleProperties makeFlexibleProperties(URL url, Properties properties) {
        return new FlexibleProperties(url, properties);
    }

    public static FlexibleProperties makeFlexibleProperties(String[] keysAndValues) {
        // if they gave me an odd number of elements
        if ((keysAndValues.length % 2) != 0) {
            throw new IllegalArgumentException("FlexibleProperties(String[] keysAndValues) cannot accept an odd number of elements!");
        }
        Properties newProperties = new Properties();

        for (int i = 0; i < keysAndValues.length; i += 2) {
            newProperties.setProperty(keysAndValues[i], keysAndValues[i + 1]);
        }

        return new FlexibleProperties(newProperties);
    }

    private void init() {
        try {
            load();
        } catch (IOException e) {
            Debug.log(e);
        }
    }

    public boolean getDoPropertyExpansion() {
        return doPropertyExpansion;
    }

    public void setDoPropertyExpansion(boolean doPropertyExpansion) {
        this.doPropertyExpansion = doPropertyExpansion;
    }

    public boolean getTruncateIfMissing() {
        return truncateIfMissing;
    }

    public void setTruncateIfMissing(boolean truncateIfMissing) {
        this.truncateIfMissing = truncateIfMissing;
    }

    public URL getURL() {
        return url;
    }

    public void setURL(URL url) {
        this.url = url;
        init();
    }

    public Properties getDefaultProperties() {
        return this.defaults;
    }

    public void setDefaultProperties(Properties defaults) {
        this.defaults = new FlexibleProperties(defaults);
    }

    protected synchronized void load() throws IOException {
        if (url == null) return;
        InputStream in = null;

        try {
            in = url.openStream();
        } catch (Exception urlex) {
            Debug.log("[FlexibleProperties.load]: Couldn't find the URL: " + url);
            Debug.log(urlex);
        }

        if (in == null) throw new IOException("Could not open resource URL " + url);

        super.load(in);
        in.close();

        if (defaults instanceof FlexibleProperties) ((FlexibleProperties) defaults).reload();
        if (getDoPropertyExpansion()) interpolateProperties();
    }

    public synchronized void store(String header) throws IOException {
        super.store(url.openConnection().getOutputStream(), header);
    }

    public synchronized void reload() throws IOException {
        Debug.log("Reloading the resource: " + url);
        this.load();
    }

    // ==== Property interpolation methods ====
    public void interpolateProperties() {
        if ((defaults != null) && (defaults instanceof FlexibleProperties)) {
            ((FlexibleProperties) defaults).interpolateProperties();
        }
        interpolateProperties(this, getTruncateIfMissing());
    }

    public static void interpolateProperties(Properties props) {
        interpolateProperties(props, truncateIfMissingDefault);
    }

    public static void interpolateProperties(Properties props, boolean truncateIfMissing) {
        Enumeration<Object> keys = props.keys();

        while (keys.hasMoreElements()) {
            String key = keys.nextElement().toString();
            String value = props.getProperty(key);

            key = interpolate(key, props, truncateIfMissing);
            props.setProperty(key, interpolate(value, props, truncateIfMissing));
        }
    }

    public static String interpolate(String value, Properties props) {
        return interpolate(value, props, truncateIfMissingDefault);
    }

    public static String interpolate(String value, Properties props, boolean truncateIfMissing) {
        return interpolate(value, props, truncateIfMissing, null);
    }

    public static String interpolate(String value, Properties props, boolean truncateIfMissing, ArrayList<String> beenThere) {
        if (props == null || value == null) return value;
        if (beenThere == null) {
            beenThere = new ArrayList<String>();
            // Debug.log("[FlexibleProperties.interpolate] starting interpolate: value=[" + value + "]");
        } else {// Debug.log("[FlexibleProperties.interpolate] starting sub-interpolate: beenThere=[" + beenThere + "], value=[" + value + "]");
        }
        int start = value.indexOf("${");

        while (start > -1) {
            int end = value.indexOf("}", (start + 2));

            if (end > start + 2) {
                String keyToExpand = value.substring((start + 2), end);
                int nestedStart = keyToExpand.indexOf("${");

                while (nestedStart > -1) {
                    end = value.indexOf("}", (end + 1));
                    if (end > -1) {
                        keyToExpand = value.substring((start + 2), end);
                        nestedStart = keyToExpand.indexOf("${", (nestedStart + 2));
                    } else {
                        Debug.log("[FlexibleProperties.interpolate] Malformed value: [" + value + "] " + "contained unbalanced start \"${\" and end \"}\" characters");
                        return value;
                    }
                }
                // if this key needs to be interpolated itself
                if (keyToExpand.contains("${")) {
                    // Debug.log("[FlexibleProperties.interpolate] recursing on key: keyToExpand=[" + keyToExpand + "]");

                    // save current beenThere and restore after so the later interpolates don't get messed up
                    ArrayList<String> tempBeenThere = new ArrayList<String>(beenThere);

                    beenThere.add(keyToExpand);
                    keyToExpand = interpolate(keyToExpand, props, truncateIfMissing, beenThere);
                    beenThere = tempBeenThere;
                }
                if (beenThere.contains(keyToExpand)) {
                    beenThere.add(keyToExpand);
                    Debug.log("[FlexibleProperties.interpolate] Recursion loop detected:  Property:[" + beenThere.get(0) + "] " + "included property: [" + keyToExpand + "]");
                    Debug.log("[FlexibleProperties.interpolate] Recursion loop path:" + beenThere);
                    return value;
                } else {
                    String expandValue = null;

                    if (keyToExpand.startsWith("env.")) {
                        String envValue = System.getProperty(keyToExpand.substring(4));

                        if (envValue == null) {
                            Debug.log("[FlexibleProperties.interpolate] ERROR: Could not find environment variable named: " + keyToExpand.substring(4));
                        } else {
                            expandValue = envValue;
                            // Debug.log("[FlexibleProperties.interpolate] Got expandValue from environment: " + expandValue);
                        }
                    } else {
                        expandValue = props.getProperty(keyToExpand);
                        // Debug.log("[FlexibleProperties.interpolate] Got expandValue from another property: " + expandValue);
                    }

                    if (expandValue != null) {
                        // Key found - interpolate

                        // if this value needs to be interpolated itself
                        if (expandValue.contains("${")) {
                            // Debug.log("[FlexibleProperties] recursing on value: expandValue=[" + expandValue + "]");
                            // save current beenThere and restore after so the later interpolates don't get messed up
                            ArrayList<String> tempBeenThere = new ArrayList<String>(beenThere);

                            beenThere.add(keyToExpand);
                            expandValue = interpolate(expandValue, props, truncateIfMissing, beenThere);
                            beenThere = tempBeenThere;
                        }
                        value = value.substring(0, start) + expandValue + value.substring(end + 1);
                        end = start + expandValue.length();

                    } else {
                        // Key not found - (expandValue == null)
                        if (truncateIfMissing == true) {
                            value = value.substring(0, start) + value.substring(end + 1);
                        }
                    }
                }
            } else {
                Debug.log("[FlexibleProperties.interpolate] Value [" + value + "] starts but does end variable");
                return value;
            }
            start = value.indexOf("${", end);
        }
        return value;
    }

    // ==== Utility/override methods ====
    public Object clone() {
        FlexibleProperties c = (FlexibleProperties) super.clone();

        // avoid recursion for some reason someone used themselves as defaults
        if (defaults != null && !this.equals(defaults)) {
            c.defaults = (FlexibleProperties) getDefaultProperties().clone();
        }
        return c;
    }

    public String toString() {
        StringBuilder retVal = new StringBuilder();

        for (Object keyObj : keySet()) {
            String key = keyObj.toString();
            String value = getProperty(key);

            retVal.append(key);
            retVal.append("=");
            retVal.append(value);
            retVal.append("\n");
        }

        return retVal.toString();
    }
}

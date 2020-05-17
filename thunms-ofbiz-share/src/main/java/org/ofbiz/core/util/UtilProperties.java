/*
 * $Id: UtilProperties.java,v 1.1 2005/04/01 05:58:06 sfarquhar Exp $
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


import java.net.URL;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;


/**
 * Generic Property Accessor with Cache - Utilities for working with properties files
 *
 * @author <a href="mailto:jonesde@ofbiz.org">David E. Jones</a>
 * @version 1.0
 */
public class UtilProperties {

    /**
     * An instance of the generic cache for storing the FlexibleProperties
     * corresponding to each properties file keyed by a String for the resource location.
     * This will be used for both non-locale and locale keyed FexibleProperties instances.
     */
    public static UtilCache<Object, FlexibleProperties> resourceCache = new UtilCache<Object, FlexibleProperties>("properties.UtilPropertiesResourceCache");

    /**
     * An instance of the generic cache for storing the FlexibleProperties
     * corresponding to each properties file keyed by a URL object
     */
    public static UtilCache<URL, FlexibleProperties> urlCache = new UtilCache<URL, FlexibleProperties>("properties.UtilPropertiesUrlCache");

    /**
     * An instance of the generic cache for storing the ResourceBundle
     * corresponding to each properties file keyed by a String for the resource location and the locale
     */
    public static UtilCache<String, ResourceBundle> bundleLocaleCache = new UtilCache<String, ResourceBundle>("properties.UtilPropertiesBundleLocaleCache");


    /**
     * Compares the specified property to the compareString, returns true if they are the same, false otherwise
     *
     * @param resource      The name of the resource - if the properties file is 'webevent.properties', the resource name is 'webevent'
     * @param name          The name of the property in the properties file
     * @param compareString The String to compare the property value to
     * @return True if the strings are the same, false otherwise
     */
    public static boolean propertyValueEquals(String resource, String name, String compareString) {
        String value = getPropertyValue(resource, name);

        if (value == null) return false;
        return value.trim().equals(compareString);
    }

    /**
     * Compares Ignoring Case the specified property to the compareString, returns true if they are the same, false otherwise
     *
     * @param resource      The name of the resource - if the properties file is 'webevent.properties', the resource name is 'webevent'
     * @param name          The name of the property in the properties file
     * @param compareString The String to compare the property value to
     * @return True if the strings are the same, false otherwise
     */
    public static boolean propertyValueEqualsIgnoreCase(String resource, String name, String compareString) {
        String value = getPropertyValue(resource, name);

        if (value == null) return false;
        return value.trim().equalsIgnoreCase(compareString);
    }

    /**
     * Returns the value of the specified property name from the specified resource/properties file.
     * If the specified property name or properties file is not found, the defaultValue is returned.
     *
     * @param resource     The name of the resource - if the properties file is 'webevent.properties', the resource name is 'webevent'
     * @param name         The name of the property in the properties file
     * @param defaultValue The value to return if the property is not found
     * @return The value of the property in the properties file, or if not found then the defaultValue
     */
    public static String getPropertyValue(String resource, String name, String defaultValue) {
        String value = getPropertyValue(resource, name);

        if (value == null || value.length() == 0)
            return defaultValue;
        else
            return value;
    }

    public static double getPropertyNumber(String resource, String name) {
        String str = getPropertyValue(resource, name);
        double strValue = 0.00000;

        try {
            strValue = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
        }
        return strValue;
    }

    /**
     * Returns the value of the specified property name from the specified resource/properties file
     *
     * @param resource The name of the resource - can be a file, class, or URL
     * @param name     The name of the property in the properties file
     * @return The value of the property in the properties file
     */
    public static String getPropertyValue(String resource, String name) {
        if (resource == null || resource.length() <= 0) return "";
        if (name == null || name.length() <= 0) return "";
        FlexibleProperties properties = resourceCache.get(resource);

        if (properties == null) {
            try {
                URL url = UtilURL.fromResource(resource);

                if (url == null) return "";
                properties = FlexibleProperties.makeFlexibleProperties(url);
                resourceCache.put(resource, properties);
            } catch (MissingResourceException e) {
                Debug.log(e.getMessage());
            }
        }
        if (properties == null) {
            Debug.log("[UtilProperties.getPropertyValue] could not find resource: " + resource);
            return "";
        }

        String value = null;

        try {
            value = properties.getProperty(name);
        } catch (Exception e) {
            Debug.log(e.getMessage());
        }
        return value == null ? "" : value.trim();
    }

    /**
     * Returns the specified resource/properties file
     *
     * @param resource The name of the resource - can be a file, class, or URL
     * @return The properties file
     */
    public static Properties getProperties(String resource) {
        if (resource == null || resource.length() <= 0)
            return null;
        FlexibleProperties properties = resourceCache.get(resource);

        if (properties == null) {
            try {
                URL url = UtilURL.fromResource(resource);

                if (url == null)
                    return null;
                properties = FlexibleProperties.makeFlexibleProperties(url);
                resourceCache.put(resource, properties);
            } catch (MissingResourceException e) {
                Debug.log(e.getMessage());
            }
        }
        if (properties == null) {
            Debug.log("[UtilProperties.getProperties] could not find resource: " + resource);
            return null;
        }
        return properties;
    }

    /**
     * Returns the specified resource/properties file
     *
     * @param resource The name of the resource - can be a file, class, or URL
     * @return The properties file
     */
    public static Properties getProperties(URL url) {
        if (url == null)
            return null;
        FlexibleProperties properties = resourceCache.get(url);

        if (properties == null) {
            try {
                properties = FlexibleProperties.makeFlexibleProperties(url);
                resourceCache.put(url, properties);
            } catch (MissingResourceException e) {
                Debug.log(e.getMessage());
            }
        }
        if (properties == null) {
            Debug.log("[UtilProperties.getProperties] could not find resource: " + url);
            return null;
        }
        return properties;
    }


    // ========= URL Based Methods ==========

    /**
     * Compares the specified property to the compareString, returns true if they are the same, false otherwise
     *
     * @param url           URL object specifying the location of the resource
     * @param name          The name of the property in the properties file
     * @param compareString The String to compare the property value to
     * @return True if the strings are the same, false otherwise
     */
    public static boolean propertyValueEquals(URL url, String name, String compareString) {
        String value = getPropertyValue(url, name);

        if (value == null) return false;
        return value.trim().equals(compareString);
    }

    /**
     * Compares Ignoring Case the specified property to the compareString, returns true if they are the same, false otherwise
     *
     * @param url           URL object specifying the location of the resource
     * @param name          The name of the property in the properties file
     * @param compareString The String to compare the property value to
     * @return True if the strings are the same, false otherwise
     */
    public static boolean propertyValueEqualsIgnoreCase(URL url, String name, String compareString) {
        String value = getPropertyValue(url, name);

        if (value == null) return false;
        return value.trim().equalsIgnoreCase(compareString);
    }

    /**
     * Returns the value of the specified property name from the specified resource/properties file.
     * If the specified property name or properties file is not found, the defaultValue is returned.
     *
     * @param url          URL object specifying the location of the resource
     * @param name         The name of the property in the properties file
     * @param defaultValue The value to return if the property is not found
     * @return The value of the property in the properties file, or if not found then the defaultValue
     */
    public static String getPropertyValue(URL url, String name, String defaultValue) {
        String value = getPropertyValue(url, name);

        if (value == null || value.length() <= 0)
            return defaultValue;
        else
            return value;
    }

    public static double getPropertyNumber(URL url, String name) {
        String str = getPropertyValue(url, name);
        double strValue = 0.00000;

        try {
            strValue = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
        }
        return strValue;
    }

    /**
     * Returns the value of the specified property name from the specified resource/properties file
     *
     * @param url  URL object specifying the location of the resource
     * @param name The name of the property in the properties file
     * @return The value of the property in the properties file
     */
    public static String getPropertyValue(URL url, String name) {
        if (url == null) return "";
        if (name == null || name.length() <= 0) return "";
        FlexibleProperties properties = urlCache.get(url);

        if (properties == null) {
            try {
                properties = FlexibleProperties.makeFlexibleProperties(url);
                urlCache.put(url, properties);
            } catch (MissingResourceException e) {
                Debug.log(e.getMessage());
            }
        }
        if (properties == null) {
            Debug.log("[UtilProperties.getPropertyValue] could not find resource: " + url);
            return null;
        }

        String value = null;

        try {
            value = properties.getProperty(name);
        } catch (Exception e) {
            Debug.log(e.getMessage());
        }
        return value == null ? "" : value.trim();
    }

    /**
     * Returns the value of a split property name from the specified resource/properties file
     * Rather than specifying the property name the value of a name.X property is specified which
     * will correspond to a value.X property whose value will be returned. X is a number from 1 to
     * whatever and all values are checked until a name.X for a certain X is not found.
     *
     * @param url  URL object specifying the location of the resource
     * @param name The name of the split property in the properties file
     * @return The value of the split property from the properties file
     */
    public static String getSplitPropertyValue(URL url, String name) {
        if (url == null) return "";
        if (name == null || name.length() <= 0) return "";

        FlexibleProperties properties = urlCache.get(url);

        if (properties == null) {
            try {
                properties = FlexibleProperties.makeFlexibleProperties(url);
                urlCache.put(url, properties);
            } catch (MissingResourceException e) {
                Debug.log(e.getMessage());
            }
        }
        if (properties == null) {
            Debug.log("[UtilProperties.getPropertyValue] could not find resource: " + url);
            return null;
        }

        String value = null;

        try {
            int curIdx = 1;
            String curName = null;

            while ((curName = properties.getProperty("name." + curIdx)) != null) {
                if (name.equals(curName)) {
                    value = properties.getProperty("value." + curIdx);
                    break;
                }
                curIdx++;
            }
        } catch (Exception e) {
            Debug.log(e.getMessage());
        }
        return value == null ? "" : value.trim();
    }


    // ========= Locale & Resource Based Methods ==========

    /**
     * Returns the value of the specified property name from the specified resource/properties file corresponding to the given locale.
     * <br>
     * <br> Two reasons why we do not use the FlexibleProperties class for this:
     * <ul>
     * <li>Doesn't support flexible locale based naming: try fname_locale (5 letter), then fname_locale (2 letter lang only), then fname</li>
     * <li>Does not support parent properties/bundles so that if the fname_locale5 file doesn't have it then fname_locale2 is tried, then the fname bundle</li>
     * </ul>
     *
     * @param resource The name of the resource - can be a file, class, or URL
     * @param name     The name of the property in the properties file
     * @param locale   The locale that the given resource will correspond to
     * @return The value of the property in the properties file
     */
    public static String getMessage(String resource, String name, Locale locale) {
        if (resource == null || resource.length() <= 0) return "";
        if (name == null || name.length() <= 0) return "";

        ResourceBundle bundle = getResourceBundle(resource, locale);
        if (bundle == null) return "";

        String value = null;
        try {
            value = bundle.getString(name);
        } catch (Exception e) {
            Debug.log(e.getMessage());
        }
        return value == null ? "" : value.trim();
    }

    /**
     * Returns the value of the specified property name from the specified resource/properties file corresponding
     * to the given locale and replacing argument place holders with the given arguments using the MessageFormat class
     *
     * @param resource  The name of the resource - can be a file, class, or URL
     * @param name      The name of the property in the properties file
     * @param locale    The locale that the given resource will correspond to
     * @param arguments An array of Objects to insert into the message argument place holders
     * @return The value of the property in the properties file
     */
    public static String getMessage(String resource, String name, Object[] arguments, Locale locale) {
        String value = getMessage(resource, name, locale);

        if (value == null || value.length() == 0) {
            return "";
        } else {
            if (arguments != null && arguments.length > 0) {
                value = MessageFormat.format(value, arguments);
            }
            return value;
        }
    }

    /**
     * Returns the value of the specified property name from the specified resource/properties file corresponding
     * to the given locale and replacing argument place holders with the given arguments using the MessageFormat class
     *
     * @param resource  The name of the resource - can be a file, class, or URL
     * @param name      The name of the property in the properties file
     * @param locale    The locale that the given resource will correspond to
     * @param arguments A list of Objects to insert into the message argument place holders
     * @return The value of the property in the properties file
     */
    public static String getMessage(String resource, String name, List<?> arguments, Locale locale) {
        String value = getMessage(resource, name, locale);

        if (value == null || value.length() == 0) {
            return "";
        } else {
            if (arguments != null && arguments.size() > 0) {
                value = MessageFormat.format(value, arguments.toArray());
            }
            return value;
        }
    }

    /**
     * Returns the specified resource/properties file as a ResourceBundle
     *
     * @param resource The name of the resource - can be a file, class, or URL
     * @param locale   The locale that the given resource will correspond to
     * @return The ResourceBundle
     */
    public static ResourceBundle getResourceBundle(String resource, Locale locale) {
        if (resource == null || resource.length() <= 0) return null;
        if (locale == null) locale = Locale.getDefault();

        String resourceCacheKey = resource + "_" + locale.toString();
        ResourceBundle bundle = bundleLocaleCache.get(resourceCacheKey);

        if (bundle == null) {
            try {
                bundle = ResourceBundle.getBundle(resource, locale);
                bundleLocaleCache.put(resourceCacheKey, bundle);
            } catch (MissingResourceException e) {
                Debug.log(e, "[UtilProperties.getPropertyValue] could not find resource: " + resource + " for locale " + locale.toString());
                return null;
            }
        }
        if (bundle == null) {
            Debug.log("[UtilProperties.getPropertyValue] could not find resource: " + resource + " for locale " + locale.toString());
            return null;
        }

        return bundle;
    }

    /**
     * Returns the specified resource/properties file
     *
     * NOTE: This is NOT fully implemented yet to fulfill all of the requirements
     * for i18n messages. Do NOT use.
     *
     * To be used in an i18n context this still needs to be extended quite
     * a bit. The behavior needed is that for each getMessage the most specific
     * locale (with fname_en_US for instance) is searched first, then the next
     * less specific (fname_en for instance), then without the locale if it is
     * still not found (plain fname for example, not that these examples would
     * have .properties appended to them).
     * This would be accomplished by returning the following structure:
     * 1. Get "fname" FlexibleProperties object
     * 2. Get the "fname_en" FlexibleProperties object and if the "fname" one
     * is not null, set it as the default/parent of the "fname_en" object
     * 3. Get the "fname_en_US" FlexibleProperties object and if the
     * "fname_en" one is not null, set it as the default/parent of the
     * "fname_en_US" object; if the "fname_en" one is null, but the "fname"
     * one is not, set the "fname" object as the default/parent of the
     * "fname_en_US" object
     * Then return the fname_en_US object if not null, else the fname_en, else the fname.
     *
     * To make this all more fun, the default locale should be the parent of
     * the "fname" object in this example so that there is an even higher
     * chance of finding something for each request.
     *
     * For efficiency all of these should be cached indendependently so the same
     * instance can be shared, speeding up loading time/efficiency.
     *
     * All of this should work with the setDefaultProperties method of the
     * FlexibleProperties class, but it should be tested and updated as
     * necessary. It's a bit tricky, so chances are it won't work as desired...
     *
     * @param resource The name of the resource - can be a file, class, or URL
     * @param locale   The locale that the given resource will correspond to
     * @return The Properties class
     */
    public static Properties getProperties(String resource, Locale locale) {
        if (resource == null || resource.length() <= 0) return null;
        if (locale == null) locale = Locale.getDefault();

        String localeString = locale.toString();
        String resourceLocale = resource + "_" + localeString;
        FlexibleProperties properties = resourceCache.get(resourceLocale);

        if (properties == null) {
            try {
                URL url = UtilURL.fromResource(resourceLocale);
                if (url == null) {
                    properties = (FlexibleProperties) getProperties(resource);
                } else {
                    properties = FlexibleProperties.makeFlexibleProperties(url);
                }
            } catch (MissingResourceException e) {
                Debug.log(e.getMessage());
            }
            resourceCache.put(resourceLocale, properties);
        }

        if (properties == null)
            Debug.logInfo("[UtilProperties.getProperties] could not find resource: " + resource + ", locale: " + locale);

        return properties;
    }
}

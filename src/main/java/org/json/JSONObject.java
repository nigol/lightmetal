package org.json;

/*
Public Domain.
*/

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * A JSONObject is an unordered collection of name/value pairs. Its external
 * form is a string wrapped in curly braces with colons between the names and
 * values, and commas between the values and names. The internal form is an
 * object having <code>get</code> and <code>opt</code> methods for accessing
 * the values by name, and <code>put</code> methods for adding or replacing
 * values by name. The values can be any of these types: <code>Boolean</code>,
 * <code>JSONArray</code>, <code>JSONObject</code>, <code>Number</code>,
 * <code>String</code>, or the <code>JSONObject.NULL</code> object. A
 * JSONObject constructor can be used to convert an external form JSON text
 * into an internal form whose values can be retrieved with the
 * <code>get</code> and <code>opt</code> methods, or to convert values into a
 * JSON text using the <code>put</code> and <code>toString</code> methods. A
 * <code>get</code> method returns a value if one can be found, and throws an
 * exception if one cannot be found. An <code>opt</code> method returns a
 * default value instead of throwing an exception, and so is useful for
 * obtaining optional values.
 * <p>
 * The generic <code>get()</code> and <code>opt()</code> methods return an
 * object, which you can cast or query for type. There are also typed
 * <code>get</code> and <code>opt</code> methods that do type checking and type
 * coercion for you. The opt methods differ from the get methods in that they
 * do not throw. Instead, they return a specified value, such as null.
 * <p>
 * The <code>put</code> methods add or replace values in an object. For
 * example,
 *
 * <pre>
 * myString = new JSONObject()
 *         .put(&quot;JSON&quot;, &quot;Hello, World!&quot;).toString();
 * </pre>
 *
 * produces the string <code>{"JSON": "Hello, World"}</code>.
 * <p>
 * The texts produced by the <code>toString</code> methods strictly conform to
 * the JSON syntax rules. The constructors are more forgiving in the texts they
 * will accept:
 * <ul>
 * <li>An extra <code>,</code>&nbsp;<small>(comma)</small> may appear just
 * before the closing brace.</li>
 * <li>Strings may be quoted with <code>'</code>&nbsp;<small>(single
 * quote)</small>.</li>
 * <li>Strings do not need to be quoted at all if they do not begin with a
 * quote or single quote, and if they do not contain leading or trailing
 * spaces, and if they do not contain any of these characters:
 * <code>{ } [ ] / \ : , #</code> and if they do not look like numbers and
 * if they are not the reserved words <code>true</code>, <code>false</code>,
 * or <code>null</code>.</li>
 * </ul>
 *
 * @author JSON.org
 * @version 2016-08-15
 */
public class JSONObject {
    /**
     * JSONObject.NULL is equivalent to the value that JavaScript calls null,
     * whilst Java's null is equivalent to the value that JavaScript calls
     * undefined.
     */
    private static final class Null {

        @Override
        @SuppressWarnings("lgtm[java/unchecked-cast-in-equals]")
        public boolean equals(Object object) {
            return object == null || object == this;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "null";
        }
    }

    /**
     *  Regular Expression Pattern that matches JSON Numbers. This is primarily used for
     *  output to guarantee that we are always writing valid JSON.
     */
    static final Pattern NUMBER_PATTERN = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    /**
     * The map where the JSONObject's properties are kept.
     */
    private final Map<String, Object> map;

    /**
     * It is sometimes more convenient and less ambiguous to have a
     * <code>NULL</code> object than to use Java's <code>null</code> value.
     * <code>JSONObject.NULL.equals(null)</code> returns <code>true</code>.
     * <code>JSONObject.NULL.toString()</code> returns <code>"null"</code>.
     */
    public static final Object NULL = new Null();

    /**
     * Construct an empty JSONObject.
     */
    public JSONObject() {
        this.map = new HashMap<>();
    }

    /**
     * Construct a JSONObject from a JSONTokener.
     *
     * @param x
     *            A JSONTokener object containing the source string.
     * @throws IllegalArgumentException
     *             If there is a syntax error in the source string or a
     *             duplicated key.
     */
    public JSONObject(JSONTokener x) {
        this(x, x.getJsonParserConfiguration());
    }

    /**
     * Construct a JSONObject from a JSONTokener with custom json parse configurations.
     *
     * @param x
     *            A JSONTokener object containing the source string.
     * @param jsonParserConfiguration
     *            Variable to pass parser custom configuration for json parsing.
     * @throws IllegalArgumentException
     *             If there is a syntax error in the source string or a
     *             duplicated key.
     */
    public JSONObject(JSONTokener x, JSONTokener.Configuration jsonParserConfiguration) {
        this();
        boolean isInitial = x.getPrevious() == 0;

        if (x.nextClean() != '{') {
            throw x.syntaxError("A JSONObject text must begin with '{'");
        }
        for (;;) {
            if (parseJSONObject(x, jsonParserConfiguration, isInitial)) {
                return;
            }
        }
    }

    private boolean parseJSONObject(JSONTokener jsonTokener, JSONTokener.Configuration jsonParserConfiguration, boolean isInitial) {
        Object obj;
        String key;
        boolean doneParsing = false;
        char c = jsonTokener.nextClean();

        switch (c) {
            case 0 -> throw jsonTokener.syntaxError("A JSONObject text must end with '}'");
            case '}' -> {
                if (isInitial && jsonParserConfiguration.strictMode() && jsonTokener.nextClean() != 0) {
                    throw jsonTokener.syntaxError("Strict mode error: Unparsed characters found at end of input text");
                }
                return true;
            }
            default -> {
                obj = jsonTokener.nextSimpleValue(c);
                key = obj.toString();
            }
        }

        checkKeyForStrictMode(jsonTokener, jsonParserConfiguration, obj);

        // The key is followed by ':'.
        c = jsonTokener.nextClean();
        if (c != ':') {
            throw jsonTokener.syntaxError("Expected a ':' after a key");
        }

        // Use syntaxError(..) to include error location
        if (key != null) {
            // Check if key exists
            boolean keyExists = this.opt(key) != null;
            if (keyExists && !jsonParserConfiguration.overwriteDuplicateKey()) {
                throw jsonTokener.syntaxError("Duplicate key \"" + key + "\"");
            }

            Object value = jsonTokener.nextValue();
            // Only add value if non-null
            if (value != null) {
                this.put(key, value);
            }
        }

        // Pairs are separated by ','.
        if (parseEndOfKeyValuePair(jsonTokener, jsonParserConfiguration, isInitial)) {
            doneParsing = true;
        }

        return doneParsing;
    }

    private static boolean parseEndOfKeyValuePair(JSONTokener jsonTokener, JSONTokener.Configuration jsonParserConfiguration, boolean isInitial) {
        return switch (jsonTokener.nextClean()) {
            case ';' -> {
                if (jsonParserConfiguration.strictMode()) {
                    throw jsonTokener.syntaxError("Strict mode error: Invalid character ';' found");
                }
                yield false;
            }
            case ',' -> {
                if (jsonTokener.nextClean() == '}') {
                    if (jsonParserConfiguration.strictMode()) {
                        throw jsonTokener.syntaxError("Strict mode error: Expected another object element");
                    }
                    yield true;
                }
                if (jsonTokener.end()) {
                    throw jsonTokener.syntaxError("A JSONObject text must end with '}'");
                }
                jsonTokener.back();
                yield false;
            }
            case '}' -> {
                if (isInitial && jsonParserConfiguration.strictMode() && jsonTokener.nextClean() != 0) {
                    throw jsonTokener.syntaxError("Strict mode error: Unparsed characters found at end of input text");
                }
                yield true;
            }
            default -> throw jsonTokener.syntaxError("Expected a ',' or '}'");
        };
    }

    private static void checkKeyForStrictMode(JSONTokener jsonTokener, JSONTokener.Configuration jsonParserConfiguration, Object obj) {
        if (jsonParserConfiguration != null && jsonParserConfiguration.strictMode()) {
            if(obj instanceof Boolean) {
                throw jsonTokener.syntaxError(String.format("Strict mode error: key '%s' cannot be boolean", obj.toString()));
            }
            if(obj == JSONObject.NULL) {
                throw jsonTokener.syntaxError(String.format("Strict mode error: key '%s' cannot be null", obj.toString()));
            }
            if(obj instanceof Number) {
                throw jsonTokener.syntaxError(String.format("Strict mode error: key '%s' cannot be number", obj.toString()));
            }
        }
    }

    /**
     * Construct a JSONObject from a Map.
     *
     * @param m
     *            A map object that can be used to initialize the contents of
     *            the JSONObject.
     * @throws IllegalArgumentException
     *            If a value in the map is non-finite number.
     * @throws NullPointerException
     *            If a key in the map is <code>null</code>
     */
    public JSONObject(Map<?, ?> m) {
      this(m, 0, new JSONTokener.Configuration());
    }

    /**
     * Construct a JSONObject from a Map with custom json parse configurations.
     *
     * @param m
     *            A map object that can be used to initialize the contents of
     *            the JSONObject.
     * @param jsonParserConfiguration
     *            Variable to pass parser custom configuration for json parsing.
     */
    public JSONObject(Map<?, ?> m, JSONTokener.Configuration jsonParserConfiguration) {
        this(m, 0, jsonParserConfiguration);
    }

    /**
     * Construct a JSONObject from a map with recursion depth.
     */
    private JSONObject(Map<?, ?> m, int recursionDepth, JSONTokener.Configuration jsonParserConfiguration) {
        if (recursionDepth > jsonParserConfiguration.maxNestingDepth()) {
          throw new IllegalStateException("JSONObject has reached recursion depth limit of " + jsonParserConfiguration.maxNestingDepth());
        }
        if (m == null) {
            this.map = new HashMap<>();
        } else {
            this.map = new HashMap<>(m.size());
        	for (final Entry<?, ?> e : m.entrySet()) {
        	    if(e.getKey() == null) {
        	        throw new NullPointerException("Null key.");
        	    }
                final Object value = e.getValue();
                if (value != null || jsonParserConfiguration.useNativeNulls()) {
                    testValidity(value);
                    this.map.put(String.valueOf(e.getKey()), wrap(value, recursionDepth + 1, jsonParserConfiguration));
                }
            }
        }
    }

    /**
     * Construct a JSONObject from a source JSON text string. This is the most
     * commonly used JSONObject constructor.
     *
     * @param source
     *            A string beginning with <code>{</code>&nbsp;<small>(left
     *            brace)</small> and ending with <code>}</code>
     *            &nbsp;<small>(right brace)</small>.
     * @exception IllegalArgumentException
     *                If there is a syntax error in the source string or a
     *                duplicated key.
     */
    public JSONObject(String source) {
        this(source, new JSONTokener.Configuration());
    }

    /**
     * Construct a JSONObject from a source JSON text string with custom json parse configurations.
     *
     * @param source
     *            A string beginning with <code>{</code>&nbsp;<small>(left
     *            brace)</small> and ending with <code>}</code>
     *            &nbsp;<small>(right brace)</small>.
     * @param jsonParserConfiguration
     *            Variable to pass parser custom configuration for json parsing.
     * @exception IllegalArgumentException
     *                If there is a syntax error in the source string or a
     *                duplicated key.
     */
    public JSONObject(String source, JSONTokener.Configuration jsonParserConfiguration) {
        this(new JSONTokener(source, jsonParserConfiguration), jsonParserConfiguration);
    }

    /**
     * Constructor to specify an initial capacity of the internal map.
     *
     * @param initialCapacity initial capacity of the internal map.
     */
    protected JSONObject(int initialCapacity){
        this.map = new HashMap<>(initialCapacity);
    }

    /**
     * Get the value object associated with a key.
     *
     * @param key
     *            A key string.
     * @return The object associated with the key.
     * @throws IllegalArgumentException
     *             if the key is not found.
     */
    public Object get(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Null key.");
        }
        Object object = this.opt(key);
        if (object == null) {
            throw new IllegalArgumentException("JSONObject[" + quote(key) + "] not found.");
        }
        return object;
    }

    /**
     * Get the boolean value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The truth.
     * @throws IllegalArgumentException
     *             if the value is not a Boolean or the String "true" or
     *             "false".
     */
    public boolean getBoolean(String key) {
        Object object = this.get(key);
        if (Boolean.FALSE.equals(object)
                || (object instanceof String s && "false".equalsIgnoreCase(s))) {
            return false;
        } else if (Boolean.TRUE.equals(object)
                || (object instanceof String s && "true".equalsIgnoreCase(s))) {
            return true;
        }
        throw wrongValueFormatException(key, "Boolean", object, null);
    }

    /**
     * Get the double value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The numeric value.
     * @throws IllegalArgumentException
     *             if the key is not found or if the value is not a Number
     *             object and cannot be converted to a number.
     */
    public double getDouble(String key) {
        final Object object = this.get(key);
        if(object instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(key, "double", object, e);
        }
    }

    /**
     * Get the int value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The integer value.
     * @throws IllegalArgumentException
     *             if the key is not found or if the value cannot be converted
     *             to an integer.
     */
    public int getInt(String key) {
        final Object object = this.get(key);
        if(object instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(key, "int", object, e);
        }
    }

    /**
     * Get the JSONArray value associated with a key.
     *
     * @param key
     *            A key string.
     * @return A JSONArray which is the value.
     * @throws IllegalArgumentException
     *             if the key is not found or if the value is not a JSONArray.
     */
    public JSONArray getJSONArray(String key) {
        Object object = this.get(key);
        if (object instanceof JSONArray ja) {
            return ja;
        }
        throw wrongValueFormatException(key, "JSONArray", object, null);
    }

    /**
     * Get the JSONObject value associated with a key.
     *
     * @param key
     *            A key string.
     * @return A JSONObject which is the value.
     * @throws IllegalArgumentException
     *             if the key is not found or if the value is not a JSONObject.
     */
    public JSONObject getJSONObject(String key) {
        Object object = this.get(key);
        if (object instanceof JSONObject jo) {
            return jo;
        }
        throw wrongValueFormatException(key, "JSONObject", object, null);
    }

    /**
     * Get the long value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The long value.
     * @throws IllegalArgumentException
     *             if the key is not found or if the value cannot be converted
     *             to a long.
     */
    public long getLong(String key) {
        final Object object = this.get(key);
        if(object instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(key, "long", object, e);
        }
    }

    /**
     * Get an array of field names from a JSONObject.
     *
     * @param jo
     *            JSON object
     * @return An array of field names, or null if there are no names.
     */
    public static String[] getNames(JSONObject jo) {
        if (jo.isEmpty()) {
            return null;
        }
        return jo.keySet().toArray(new String[jo.length()]);
    }

    /**
     * Get the string associated with a key.
     *
     * @param key
     *            A key string.
     * @return A string which is the value.
     * @throws IllegalArgumentException
     *             if there is no string value for the key.
     */
    public String getString(String key) {
        Object object = this.get(key);
        if (object instanceof String s) {
            return s;
        }
        throw wrongValueFormatException(key, "string", object, null);
    }

    /**
     * Determine if the JSONObject contains a specific key.
     *
     * @param key
     *            A key string.
     * @return true if the key exists in the JSONObject.
     */
    public boolean has(String key) {
        return this.map.containsKey(key);
    }

    /**
     * Determine if the value associated with the key is <code>null</code> or if there is no
     * value.
     *
     * @param key
     *            A key string.
     * @return true if there is no value associated with the key or if the value
     *        is the JSONObject.NULL object.
     */
    public boolean isNull(String key) {
        return JSONObject.NULL.equals(this.opt(key));
    }

    /**
     * Get an enumeration of the keys of the JSONObject. Modifying this key Set will also
     * modify the JSONObject. Use with caution.
     *
     * @see Set#iterator()
     *
     * @return An iterator of the keys.
     */
    public Iterator<String> keys() {
        return this.keySet().iterator();
    }

    /**
     * Get a set of keys of the JSONObject. Modifying this key Set will also modify the
     * JSONObject. Use with caution.
     *
     * @see Map#keySet()
     *
     * @return A keySet.
     */
    public Set<String> keySet() {
        return this.map.keySet();
    }

    /**
     * Get a set of entries of the JSONObject. These are raw values and may not
     * match what is returned by the JSONObject get* and opt* functions. Modifying
     * the returned EntrySet or the Entry objects contained therein will modify the
     * backing JSONObject. This does not return a clone or a read-only view.
     *
     * Use with caution.
     *
     * @see Map#entrySet()
     *
     * @return An Entry Set
     */
    protected Set<Entry<String, Object>> entrySet() {
        return this.map.entrySet();
    }

    /**
     * Get the number of keys stored in the JSONObject.
     *
     * @return The number of keys in the JSONObject.
     */
    public int length() {
        return this.map.size();
    }

    /**
     * Removes all of the elements from this JSONObject.
     * The JSONObject will be empty after this call returns.
     */
    public void clear() {
        this.map.clear();
    }

    /**
     * Check if JSONObject is empty.
     *
     * @return true if JSONObject is empty, otherwise false.
     */
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    /**
     * Produce a string from a Number.
     *
     * @param number
     *            A Number
     * @return A String.
     * @throws IllegalArgumentException
     *             If n is a non-finite number.
     */
    public static String numberToString(Number number) {
        if (number == null) {
            throw new IllegalArgumentException("Null pointer");
        }
        testValidity(number);

        // Shave off trailing zeros and decimal point, if possible.
        String string = number.toString();
        if (string.indexOf('.') > 0 && string.indexOf('e') < 0
                && string.indexOf('E') < 0) {
            while (string.endsWith("0")) {
                string = string.substring(0, string.length() - 1);
            }
            if (string.endsWith(".")) {
                string = string.substring(0, string.length() - 1);
            }
        }
        return string;
    }

    /**
     * Get an optional value associated with a key.
     *
     * @param key
     *            A key string.
     * @return An object which is the value, or null if there is no value.
     */
    public Object opt(String key) {
        return key == null ? null : this.map.get(key);
    }

    /**
     * Get an optional boolean associated with a key. It returns false if there
     * is no such key, or if the value is not Boolean.TRUE or the String "true".
     *
     * @param key
     *            A key string.
     * @return The truth.
     */
    public boolean optBoolean(String key) {
        return this.optBoolean(key, false);
    }

    /**
     * Get an optional boolean associated with a key. It returns the
     * defaultValue if there is no such key, or if it is not a Boolean or the
     * String "true" or "false" (case insensitive).
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return The truth.
     */
    public boolean optBoolean(String key, boolean defaultValue) {
        Object val = this.opt(key);
        if (NULL.equals(val)) {
            return defaultValue;
        }
        if (val instanceof Boolean b){
            return b.booleanValue();
        }
        try {
            return this.getBoolean(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional double associated with a key, or NaN if there is no such
     * key or if its value is not a number. If the value is a string, an attempt
     * will be made to evaluate it as a number.
     *
     * @param key
     *            A string which is the key.
     * @return An object which is the value.
     */
    public double optDouble(String key) {
        return this.optDouble(key, Double.NaN);
    }

    /**
     * Get an optional double associated with a key, or the defaultValue if
     * there is no such key or if its value is not a number. If the value is a
     * string, an attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public double optDouble(String key, double defaultValue) {
        Object val = this.opt(key);
        if (NULL.equals(val)) {
            return defaultValue;
        }
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return stringToNumber(val.toString()).doubleValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional int value associated with a key, or zero if there is no
     * such key or if the value is not a number. If the value is a string, an
     * attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @return An object which is the value.
     */
    public int optInt(String key) {
        return this.optInt(key, 0);
    }

    /**
     * Get an optional int value associated with a key, or the default if there
     * is no such key or if the value is not a number. If the value is a string,
     * an attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public int optInt(String key, int defaultValue) {
        Object val = this.opt(key);
        if (NULL.equals(val)) {
            return defaultValue;
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        try {
            return stringToNumber(val.toString()).intValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional JSONArray associated with a key. It returns null if there
     * is no such key, or if its value is not a JSONArray.
     *
     * @param key
     *            A key string.
     * @return A JSONArray which is the value.
     */
    public JSONArray optJSONArray(String key) {
        return this.optJSONArray(key, null);
    }

    /**
     * Get an optional JSONArray associated with a key, or the default if there
     * is no such key, or if its value is not a JSONArray.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return A JSONArray which is the value.
     */
    public JSONArray optJSONArray(String key, JSONArray defaultValue) {
        Object object = this.opt(key);
        return object instanceof JSONArray ja ? ja : defaultValue;
    }

    /**
     * Get an optional JSONObject associated with a key. It returns null if
     * there is no such key, or if its value is not a JSONObject.
     *
     * @param key
     *            A key string.
     * @return A JSONObject which is the value.
     */
    public JSONObject optJSONObject(String key) { return this.optJSONObject(key, null); }

    /**
     * Get an optional JSONObject associated with a key, or the default if there
     * is no such key or if the value is not a JSONObject.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An JSONObject which is the value.
     */
    public JSONObject optJSONObject(String key, JSONObject defaultValue) {
        Object object = this.opt(key);
        return object instanceof JSONObject jo ? jo : defaultValue;
    }

    /**
     * Get an optional long value associated with a key, or zero if there is no
     * such key or if the value is not a number. If the value is a string, an
     * attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @return An object which is the value.
     */
    public long optLong(String key) {
        return this.optLong(key, 0);
    }

    /**
     * Get an optional long value associated with a key, or the default if there
     * is no such key or if the value is not a number. If the value is a string,
     * an attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public long optLong(String key, long defaultValue) {
        Object val = this.opt(key);
        if (NULL.equals(val)) {
            return defaultValue;
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        try {
            return stringToNumber(val.toString()).longValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional string associated with a key. It returns an empty string
     * if there is no such key. If the value is not a string and is not null,
     * then it is converted to a string.
     *
     * @param key
     *            A key string.
     * @return A string which is the value.
     */
    public String optString(String key) {
        return this.optString(key, "");
    }

    /**
     * Get an optional string associated with a key. It returns the defaultValue
     * if there is no such key.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return A string which is the value.
     */
    public String optString(String key, String defaultValue) {
        Object object = this.opt(key);
        return NULL.equals(object) ? defaultValue : object.toString();
    }

    /**
     * Put a key/boolean pair in the JSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            A boolean which is the value.
     * @return this.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, boolean value) {
        return this.put(key, value ? Boolean.TRUE : Boolean.FALSE);
    }

    /**
     * Put a key/value pair in the JSONObject, where the value will be a
     * JSONArray which is produced from a Collection.
     *
     * @param key
     *            A key string.
     * @param value
     *            A Collection value.
     * @return this.
     * @throws IllegalArgumentException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, Collection<?> value) {
        return this.put(key, new JSONArray(value));
    }

    /**
     * Put a key/double pair in the JSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            A double which is the value.
     * @return this.
     * @throws IllegalArgumentException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, double value) {
        return this.put(key, Double.valueOf(value));
    }

    /**
     * Put a key/float pair in the JSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            A float which is the value.
     * @return this.
     * @throws IllegalArgumentException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, float value) {
        return this.put(key, Float.valueOf(value));
    }

    /**
     * Put a key/int pair in the JSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            An int which is the value.
     * @return this.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, int value) {
        return this.put(key, Integer.valueOf(value));
    }

    /**
     * Put a key/long pair in the JSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            A long which is the value.
     * @return this.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, long value) {
        return this.put(key, Long.valueOf(value));
    }

    /**
     * Put a key/value pair in the JSONObject, where the value will be a
     * JSONObject which is produced from a Map.
     *
     * @param key
     *            A key string.
     * @param value
     *            A Map value.
     * @return this.
     * @throws IllegalArgumentException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, Map<?, ?> value) {
        return this.put(key, new JSONObject(value));
    }

    /**
     * Put a key/value pair in the JSONObject. If the value is <code>null</code>, then the
     * key will be removed from the JSONObject if it is present.
     *
     * @param key
     *            A key string.
     * @param value
     *            An object which is the value. It should be of one of these
     *            types: Boolean, Double, Integer, JSONArray, JSONObject, Long,
     *            String, or the JSONObject.NULL object.
     * @return this.
     * @throws IllegalArgumentException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, Object value) {
        if (key == null) {
            throw new NullPointerException("Null key.");
        }
        if (value != null) {
            testValidity(value);
            this.map.put(key, value);
        } else {
            this.remove(key);
        }
        return this;
    }

    /**
     * Put a key/value pair in the JSONObject, but only if the key and the value
     * are both non-null.
     *
     * @param key
     *            A key string.
     * @param value
     *            An object which is the value. It should be of one of these
     *            types: Boolean, Double, Integer, JSONArray, JSONObject, Long,
     *            String, or the JSONObject.NULL object.
     * @return this.
     * @throws IllegalArgumentException
     *             If the value is a non-finite number.
     */
    public JSONObject putOpt(String key, Object value) {
        if (key != null && value != null) {
            return this.put(key, value);
        }
        return this;
    }

    /**
     * Produce a string in double quotes with backslash sequences in all the
     * right places. A backslash will be inserted within &lt;/, producing
     * &lt;\/, allowing JSON text to be delivered in HTML. In JSON text, a
     * string cannot contain a control character or an unescaped quote or
     * backslash.
     *
     * @param string
     *            A String
     * @return A String correctly formatted for insertion in a JSON text.
     */
    public static String quote(String string) {
        if (string == null || string.isEmpty()) {
            return "\"\"";
        }
        var sb = new StringBuilder(string.length() + 2);
        try {
            return quote(string, sb).toString();
        } catch (IOException ignored) {
            // will never happen - we are writing to a StringBuilder
            return "";
        }
    }

    public static Appendable quote(String string, Appendable w) throws IOException {
        if (string == null || string.isEmpty()) {
            w.append("\"\"");
            return w;
        }

        char b;
        char c = 0;
        int i;
        int len = string.length();

        w.append('"');
        for (i = 0; i < len; i += 1) {
            b = c;
            c = string.charAt(i);
            switch (c) {
            case '\\', '"' -> {
                w.append('\\');
                w.append(c);
            }
            case '/' -> {
                if (b == '<') {
                    w.append('\\');
                }
                w.append(c);
            }
            case '\b' -> w.append("\\b");
            case '\t' -> w.append("\\t");
            case '\n' -> w.append("\\n");
            case '\f' -> w.append("\\f");
            case '\r' -> w.append("\\r");
            default -> writeAsHex(w, c);
            }
        }
        w.append('"');
        return w;
    }

    private static void writeAsHex(Appendable w, char c) throws IOException {
        String hhhh;
        if (c < ' ' || (c >= '\u0080' && c < '\u00a0')
                || (c >= '\u2000' && c < '\u2100')) {
            w.append("\\u");
            hhhh = Integer.toHexString(c);
            w.append("0000", 0, 4 - hhhh.length());
            w.append(hhhh);
        } else {
            w.append(c);
        }
    }

    /**
     * Remove a name and its value, if present.
     *
     * @param key
     *            The name to be removed.
     * @return The value that was associated with the name, or null if there was
     *         no value.
     */
    public Object remove(String key) {
        return this.map.remove(key);
    }

    /**
     * Tests if the value should be tried as a decimal. It makes no test if there are actual digits.
     *
     * @param val value to test
     * @return true if the string is "-0" or if it contains '.', 'e', or 'E', false otherwise.
     */
    protected static boolean isDecimalNotation(final String val) {
        return val.indexOf('.') > -1 || val.indexOf('e') > -1
                || val.indexOf('E') > -1 || "-0".equals(val);
    }

    /**
     * Try to convert a string into a number, boolean, or null. If the string
     * can't be converted, return the string.
     *
     * @param string
     *            A String. can not be null.
     * @return A simple JSON value.
     * @throws NullPointerException
     *             Thrown if the string is null.
     */
    public static Object stringToValue(String string) {
        if ("".equals(string)) {
            return string;
        }

        // check JSON key words true/false/null
        if ("true".equalsIgnoreCase(string)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(string)) {
            return Boolean.FALSE;
        }
        if ("null".equalsIgnoreCase(string)) {
            return JSONObject.NULL;
        }

        char initial = string.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            try {
                return stringToNumber(string);
            } catch (Exception ignore) {
                // Do nothing
            }
        }
        return string;
    }

    /**
     * Converts a string to a number using the narrowest possible type. Possible
     * returns for this function are BigDecimal, Double, BigInteger, Long, and Integer.
     * When a Double is returned, it should always be a valid Double and not NaN or +-infinity.
     *
     * @param val value to convert
     * @return Number representation of the value.
     * @throws NumberFormatException thrown if the value is not a valid number.
     */
    protected static Number stringToNumber(final String val) throws NumberFormatException {
        char initial = val.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            // decimal representation
            if (isDecimalNotation(val)) {
                return getNumber(val, initial);
            }
            // block items like 00 01 etc. Java number parsers treat these as Octal.
            checkForInvalidNumberFormat(val, initial);
            var bi = new BigInteger(val);
            if(bi.bitLength() <= 31){
                return Integer.valueOf(bi.intValue());
            }
            if(bi.bitLength() <= 63){
                return Long.valueOf(bi.longValue());
            }
            return bi;
        }
        throw new NumberFormatException("val ["+val+"] is not a valid number.");
    }

    private static void checkForInvalidNumberFormat(String val, char initial) {
        if(initial == '0' && val.length() > 1) {
            char at1 = val.charAt(1);
            if(at1 >= '0' && at1 <= '9') {
                throw new NumberFormatException("val ["+ val +"] is not a valid number.");
            }
        } else if (initial == '-' && val.length() > 2) {
            char at1 = val.charAt(1);
            char at2 = val.charAt(2);
            if(at1 == '0' && at2 >= '0' && at2 <= '9') {
                throw new NumberFormatException("val ["+ val +"] is not a valid number.");
            }
        }
    }

    private static Number getNumber(String val, char initial) {
        try {
            var bd = new BigDecimal(val);
            if(initial == '-' && BigDecimal.ZERO.compareTo(bd)==0) {
                return Double.valueOf(-0.0);
            }
            return bd;
        } catch (NumberFormatException retryAsDouble) {
            try {
                Double d = Double.valueOf(val);
                if(d.isNaN() || d.isInfinite()) {
                    throw new NumberFormatException("val ["+ val +"] is not a valid number.");
                }
                return d;
            } catch (NumberFormatException ignore) {
                throw new NumberFormatException("val ["+ val +"] is not a valid number.");
            }
        }
    }

    /**
     * Throw an exception if the object is a NaN or infinite number.
     *
     * @param o
     *            The object to test.
     * @throws IllegalArgumentException
     *             If o is a non-finite number.
     */
    public static void testValidity(Object o) {
        if (o instanceof Number n && !numberIsFinite(n)) {
            throw new IllegalArgumentException("JSON does not allow non-finite numbers.");
        }
    }

    private static boolean numberIsFinite(Number n) {
        if (n instanceof Double d && (d.isInfinite() || d.isNaN())) {
            return false;
        } else if (n instanceof Float f && (f.isInfinite() || f.isNaN())) {
            return false;
        }
        return true;
    }

    /**
     * Make a JSON text of this JSONObject. For compactness, no whitespace is
     * added. If this would not result in a syntactically correct JSON text,
     * then null will be returned instead.
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     *
     * @return a printable, displayable, portable, transmittable representation
     *         of the object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     */
    @Override
    public String toString() {
        try {
            return this.toString(0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Make a pretty-printed JSON text of this JSONObject.
     *
     * <p>If <pre>{@code indentFactor > 0}</pre> and the {@link JSONObject}
     * has only one key, then the object will be output on a single line:
     * <pre>{@code {"key": 1}}</pre>
     *
     * <p>If an object has 2 or more keys, then it will be output across
     * multiple lines: <pre>{@code {
     *  "key1": 1,
     *  "key2": "value 2",
     *  "key3": 3
     * }}</pre>
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     *
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @return a printable, displayable, portable, transmittable representation
     *         of the object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     * @throws IllegalArgumentException
     *             If the object contains an invalid number.
     */
    public String toString(int indentFactor) {
        int initialSize = map.size() * 6;
        var sb = new StringBuilder(Math.max(initialSize, 16));
        return this.write(sb, indentFactor, 0).toString();
    }

    /**
     * Make a JSON text of an Object value. If the object has an
     * value.toJSONString() method, then that method will be used to produce the
     * JSON text. The method is required to produce a strictly conforming text.
     * If the object does not contain a toJSONString method (which is the most
     * common case), then a text will be produced by other means. If the value
     * is an array or Collection, then a JSONArray will be made from it and its
     * toJSONString method will be called. If the value is a MAP, then a
     * JSONObject will be made from it and its toJSONString method will be
     * called. Otherwise, the value's toString method will be called, and the
     * result will be quoted.
     *
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param value
     *            The value to be serialized.
     * @return a printable, displayable, transmittable representation of the
     *         object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     * @throws IllegalArgumentException
     *             If the value is or contains an invalid number.
     */
    public static String valueToString(Object value) {
        if (value == null || value.equals(null)) {
            return "null";
        }
        if (value instanceof Number number) {
            var numberAsString = numberToString(number);
            if(NUMBER_PATTERN.matcher(numberAsString).matches()) {
                return numberAsString;
            }
            return quote(numberAsString);
        }
        if (value instanceof Boolean || value instanceof JSONObject
                || value instanceof JSONArray) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            return new JSONObject(map).toString();
        }
        if (value instanceof Collection<?> coll) {
            return new JSONArray(coll).toString();
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            var ja = new JSONArray(len);
            for (int i = 0; i < len; i++) {
                ja.put(wrap(Array.get(value, i)));
            }
            return ja.toString();
        }
        if(value instanceof Enum<?> e){
            return quote(e.name());
        }
        return quote(value.toString());
    }

    /**
     * Wrap an object, if necessary. If the object is <code>null</code>, return the NULL
     * object. If it is an array or collection, wrap it in a JSONArray. If it is
     * a map, wrap it in a JSONObject. If it is a standard property (Double,
     * String, et al) then it is already wrapped. Otherwise, if it comes from
     * one of the java packages, turn it into a string. And if it doesn't, try
     * to wrap it in a JSONObject. If the wrapping fails, then null is returned.
     *
     * @param object
     *            The object to wrap
     * @return The wrapped value
     */
    public static Object wrap(Object object) {
        return wrap(object, 0, new JSONTokener.Configuration());
    }

    /**
     * Wrap an object, if necessary, with recursion depth tracking.
     *
     * @param object
     *            The object to wrap
     * @param recursionDepth
     *            Variable for tracking the count of nested object creations.
     * @param jsonParserConfiguration
     *            Variable to pass parser custom configuration for json parsing.
     * @return The wrapped value
     */
    static Object wrap(Object object, int recursionDepth, JSONTokener.Configuration jsonParserConfiguration) {
        try {
            if (NULL.equals(object)) {
                return NULL;
            }
            if (object instanceof JSONObject || object instanceof JSONArray
                    || object instanceof String
                    || object instanceof Byte || object instanceof Character
                    || object instanceof Short || object instanceof Integer
                    || object instanceof Long || object instanceof Boolean
                    || object instanceof Float || object instanceof Double
                    || object instanceof BigInteger || object instanceof BigDecimal
                    || object instanceof Enum) {
                return object;
            }

            if (object instanceof Collection<?> coll) {
                return new JSONArray(coll, recursionDepth, jsonParserConfiguration);
            }
            if (object.getClass().isArray()) {
                int length = Array.getLength(object);
                var ja = new JSONArray(length);
                for (int i = 0; i < length; i++) {
                    ja.put(wrap(Array.get(object, i), recursionDepth + 1, jsonParserConfiguration));
                }
                return ja;
            }
            if (object instanceof Map<?, ?> map) {
                return new JSONObject(map, recursionDepth, jsonParserConfiguration);
            }
            return object.toString();
        }
        catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Write the contents of the JSONObject as JSON text to an Appendable. For
     * compactness, no whitespace is added.
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     * @param writer the appendable object
     * @return The appendable.
     */
    public Appendable write(Appendable writer) {
        return this.write(writer, 0, 0);
    }

    @SuppressWarnings("resource")
    static final Appendable writeValue(Appendable writer, Object value,
            int indentFactor, int indent) throws IOException {
        if (value == null || value.equals(null)) {
            writer.append("null");
        } else if (value instanceof String) {
            quote(value.toString(), writer);
            return writer;
        } else if (value instanceof Number n) {
            processNumberToWriteValue(writer, n);
        } else if (value instanceof Boolean) {
            writer.append(value.toString());
        } else if (value instanceof Enum<?> e) {
            writer.append(quote(e.name()));
        } else if (value instanceof JSONObject jo) {
            jo.write(writer, indentFactor, indent);
        } else if (value instanceof JSONArray ja) {
            ja.write(writer, indentFactor, indent);
        } else if (value instanceof Map<?, ?> map) {
            new JSONObject(map).write(writer, indentFactor, indent);
        } else if (value instanceof Collection<?> coll) {
            new JSONArray(coll).write(writer, indentFactor, indent);
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            var ja = new JSONArray(length);
            for (int i = 0; i < length; i++) {
                ja.put(wrap(Array.get(value, i)));
            }
            ja.write(writer, indentFactor, indent);
        } else {
            quote(value.toString(), writer);
        }
        return writer;
    }

    private static void processNumberToWriteValue(Appendable writer, Number value) throws IOException {
        final String numberAsString = numberToString(value);
        if(NUMBER_PATTERN.matcher(numberAsString).matches()) {
            writer.append(numberAsString);
        } else {
            quote(numberAsString, writer);
        }
    }

    static final void indent(Appendable writer, int indent) throws IOException {
        for (int i = 0; i < indent; i += 1) {
            writer.append(' ');
        }
    }

    /**
     * Write the contents of the JSONObject as JSON text to an Appendable.
     *
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     *
     * @param writer
     *            Writes the serialized JSON
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @param indent
     *            The indentation of the top level.
     * @return The appendable.
     */
    @SuppressWarnings("resource")
    public Appendable write(Appendable writer, int indentFactor, int indent) {
        try {
            boolean needsComma = false;
            final int length = this.length();
            writer.append('{');

            if (length == 1) {
            	final Entry<String,?> entry = this.entrySet().iterator().next();
                final String key = entry.getKey();
                writer.append(quote(key));
                writer.append(':');
                if (indentFactor > 0) {
                    writer.append(' ');
                }
                attemptWriteValue(writer, indentFactor, indent, entry, key);
            } else if (length != 0) {
                writeContent(writer, indentFactor, indent, needsComma);
            }
            writer.append('}');
            return writer;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writeContent(Appendable writer, int indentFactor, int indent, boolean needsComma) throws IOException {
        final int newIndent = indent + indentFactor;
        for (final Entry<String,?> entry : this.entrySet()) {
            if (needsComma) {
                writer.append(',');
            }
            if (indentFactor > 0) {
                writer.append('\n');
            }
            indent(writer, newIndent);
            final String key = entry.getKey();
            writer.append(quote(key));
            writer.append(':');
            if (indentFactor > 0) {
                writer.append(' ');
            }
            attemptWriteValue(writer, indentFactor, newIndent, entry, key);
            needsComma = true;
        }
        if (indentFactor > 0) {
            writer.append('\n');
        }
        indent(writer, indent);
    }

    private static void attemptWriteValue(Appendable writer, int indentFactor, int indent, Entry<String, ?> entry, String key) {
        try{
            writeValue(writer, entry.getValue(), indentFactor, indent);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to write JSONObject value for key: " + key, e);
        }
    }

    /**
     * Returns a java.util.Map containing all of the entries in this object.
     * If an entry in the object is a JSONArray or JSONObject it will also
     * be converted.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return a java.util.Map containing the entries of this object
     */
    public Map<String, Object> toMap() {
        Map<String, Object> results = new HashMap<>();
        for (Entry<String, Object> entry : this.entrySet()) {
            Object value;
            if (entry.getValue() == null || NULL.equals(entry.getValue())) {
                value = null;
            } else if (entry.getValue() instanceof JSONObject jo) {
                value = jo.toMap();
            } else if (entry.getValue() instanceof JSONArray ja) {
                value = ja.toList();
            } else {
                value = entry.getValue();
            }
            results.put(entry.getKey(), value);
        }
        return results;
    }

    private static IllegalArgumentException wrongValueFormatException(
            String key,
            String valueType,
            Object value,
            Throwable cause) {
        if(value == null) {
            return new IllegalArgumentException(
                    "JSONObject[" + quote(key) + "] is not a " + valueType + " (null)."
                    , cause);
        }
        if(value instanceof Map || value instanceof Iterable || value instanceof JSONObject) {
            return new IllegalArgumentException(
                    "JSONObject[" + quote(key) + "] is not a " + valueType + " (" + value.getClass() + ")."
                    , cause);
        }
        return new IllegalArgumentException(
                "JSONObject[" + quote(key) + "] is not a " + valueType + " (" + value.getClass() + " : " + value + ")."
                , cause);
    }

    /**
     * Writer provides a quick and convenient way of producing JSON text.
     * The texts produced strictly conform to JSON syntax rules. No whitespace is
     * added, so the results are ready for transmission or storage. Each instance of
     * Writer can produce one JSON text.
     */
    public static class Writer {
        private static final int maxdepth = 200;

        private boolean comma;

        /**
         * The current mode. Values:
         * 'a' (array),
         * 'd' (done),
         * 'i' (initial),
         * 'k' (key),
         * 'o' (object).
         */
        protected char mode;

        private final JSONObject[] stack;
        private int top;
        protected Appendable writer;

        public Writer(Appendable w) {
            this.comma = false;
            this.mode = 'i';
            this.stack = new JSONObject[maxdepth];
            this.top = 0;
            this.writer = w;
        }

        private Writer append(String string) {
            if (string == null) {
                throw new IllegalArgumentException("Null pointer");
            }
            if (this.mode == 'o' || this.mode == 'a') {
                try {
                    if (this.comma && this.mode == 'a') {
                        this.writer.append(',');
                    }
                    this.writer.append(string);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (this.mode == 'o') {
                    this.mode = 'k';
                }
                this.comma = true;
                return this;
            }
            throw new IllegalStateException("Value out of sequence.");
        }

        public Writer array() {
            if (this.mode == 'i' || this.mode == 'o' || this.mode == 'a') {
                this.push(null);
                this.append("[");
                this.comma = false;
                return this;
            }
            throw new IllegalStateException("Misplaced array.");
        }

        private Writer end(char m, char c) {
            if (this.mode != m) {
                throw new IllegalStateException(m == 'a'
                    ? "Misplaced endArray."
                    : "Misplaced endObject.");
            }
            this.pop(m);
            try {
                this.writer.append(c);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            this.comma = true;
            return this;
        }

        public Writer endArray() {
            return this.end('a', ']');
        }

        public Writer endObject() {
            return this.end('k', '}');
        }

        public Writer key(String string) {
            if (string == null) {
                throw new IllegalArgumentException("Null key.");
            }
            if (this.mode == 'k') {
                try {
                    var topObject = this.stack[this.top - 1];
                    if(topObject.has(string)) {
                        throw new IllegalStateException("Duplicate key \"" + string + "\"");
                    }
                    topObject.put(string, true);
                    if (this.comma) {
                        this.writer.append(',');
                    }
                    this.writer.append(quote(string));
                    this.writer.append(':');
                    this.comma = false;
                    this.mode = 'o';
                    return this;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            throw new IllegalStateException("Misplaced key.");
        }

        public Writer object() {
            if (this.mode == 'i') {
                this.mode = 'o';
            }
            if (this.mode == 'o' || this.mode == 'a') {
                this.append("{");
                this.push(new JSONObject());
                this.comma = false;
                return this;
            }
            throw new IllegalStateException("Misplaced object.");
        }

        private void pop(char c) {
            if (this.top <= 0) {
                throw new IllegalStateException("Nesting error.");
            }
            char m = this.stack[this.top - 1] == null ? 'a' : 'k';
            if (m != c) {
                throw new IllegalStateException("Nesting error.");
            }
            this.top -= 1;
            this.mode = this.top == 0
                ? 'd'
                : this.stack[this.top - 1] == null
                ? 'a'
                : 'k';
        }

        private void push(JSONObject jo) {
            if (this.top >= maxdepth) {
                throw new IllegalStateException("Nesting too deep.");
            }
            this.stack[this.top] = jo;
            this.mode = jo == null ? 'a' : 'k';
            this.top += 1;
        }

        public Writer value(boolean b) {
            return this.append(b ? "true" : "false");
        }

        public Writer value(double d) {
            return this.value(Double.valueOf(d));
        }

        public Writer value(long l) {
            return this.append(Long.toString(l));
        }

        public Writer value(Object object) {
            return this.append(valueToString(object));
        }

        public static Writer create() {
            return new Writer(new StringBuilder());
        }

        @Override
        public String toString() {
            if (this.mode == 'd' && this.writer instanceof StringBuilder sb) {
                return sb.toString();
            }
            return null;
        }
    }
}

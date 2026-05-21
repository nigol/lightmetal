package org.json;

/*
Public Domain.
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * A JSONArray is an ordered sequence of values. Its external text form is a
 * string wrapped in square brackets with commas separating the values. The
 * internal form is an object having <code>get</code> and <code>opt</code>
 * methods for accessing the values by index, and <code>put</code> methods for
 * adding or replacing values. The values can be any of these types:
 * <code>Boolean</code>, <code>JSONArray</code>, <code>JSONObject</code>,
 * <code>Number</code>, <code>String</code>, or the
 * <code>JSONObject.NULL object</code>.
 * <p>
 * The constructor can convert a JSON text into a Java object. The
 * <code>toString</code> method converts to JSON text.
 * <p>
 * A <code>get</code> method returns a value if one can be found, and throws an
 * exception if one cannot be found. An <code>opt</code> method returns a
 * default value instead of throwing an exception, and so is useful for
 * obtaining optional values.
 * <p>
 * The generic <code>get()</code> and <code>opt()</code> methods return an
 * object which you can cast or query for type. There are also typed
 * <code>get</code> and <code>opt</code> methods that do type checking and type
 * coercion for you.
 * <p>
 * The texts produced by the <code>toString</code> methods strictly conform to
 * JSON syntax rules. The constructors are more forgiving in the texts they will
 * accept:
 * <ul>
 * <li>An extra <code>,</code>&nbsp;<small>(comma)</small> may appear just
 * before the closing bracket.</li>
 * <li>The <code>null</code> value will be inserted when there is <code>,</code>
 * &nbsp;<small>(comma)</small> elision.</li>
 * <li>Strings may be quoted with <code>'</code>&nbsp;<small>(single
 * quote)</small>.</li>
 * <li>Strings do not need to be quoted at all if they do not begin with a quote
 * or single quote, and if they do not contain leading or trailing spaces, and
 * if they do not contain any of these characters:
 * <code>{ } [ ] / \ : , #</code> and if they do not look like numbers and
 * if they are not the reserved words <code>true</code>, <code>false</code>, or
 * <code>null</code>.</li>
 * </ul>
 *
 * @author JSON.org
 * @version 2016-08/15
 */
public class JSONArray implements Iterable<Object> {

    /**
     * The arrayList where the JSONArray's properties are kept.
     */
    private final ArrayList<Object> myArrayList;

    /**
     * Construct an empty JSONArray.
     */
    public JSONArray() {
        this.myArrayList = new ArrayList<>();
    }

    /**
     * Construct a JSONArray from a JSONTokener.
     *
     * @param x
     *            A JSONTokener
     */
    public JSONArray(JSONTokener x) {
        this(x, x.getJsonParserConfiguration());
    }

    /**
     * Constructs a JSONArray from a JSONTokener and a JSONTokener.Configuration.
     *
     * @param x                       A JSONTokener instance from which the JSONArray is constructed.
     * @param jsonParserConfiguration A JSONTokener.Configuration instance that controls the behavior of the parser.
     */
    public JSONArray(JSONTokener x, JSONTokener.Configuration jsonParserConfiguration) {
        this();

        boolean isInitial = x.getPrevious() == 0;
        if (x.nextClean() != '[') {
            throw x.syntaxError("A JSONArray text must start with '['");
        }

        char nextChar = x.nextClean();
        if (nextChar == 0) {
            // array is unclosed. No ']' found, instead EOF
            throw x.syntaxError("Expected a ',' or ']'");
        } else if (nextChar==',' && jsonParserConfiguration.strictMode()) {
        	 throw x.syntaxError("Array content starts with a ','");
        }
        if (nextChar != ']') {
            x.back();
            for (;;) {
                if (x.nextClean() == ',') {
                    x.back();
                    this.myArrayList.add(JSONObject.NULL);
                } else {
                    x.back();
                    this.myArrayList.add(x.nextValue());
                }
                if (checkForSyntaxError(x, jsonParserConfiguration, isInitial)) return;
            }
        } else {
            if (isInitial && jsonParserConfiguration.strictMode() && x.nextClean() != 0) {
                throw x.syntaxError("Strict mode error: Unparsed characters found at end of input text");
            }
        }
    }

    private static boolean checkForSyntaxError(JSONTokener x, JSONTokener.Configuration jsonParserConfiguration, boolean isInitial) {
        return switch (x.nextClean()) {
            case 0 ->
                throw x.syntaxError("Expected a ',' or ']'");
            case ',' -> {
                char nextChar = x.nextClean();
                if (nextChar == 0) {
                    throw x.syntaxError("Expected a ',' or ']'");
                }
                if (nextChar == ']') {
                    if (jsonParserConfiguration.strictMode()) {
                        throw x.syntaxError("Strict mode error: Expected another array element");
                    }
                    yield true;
                }
                if (nextChar == ',') {
                    if (jsonParserConfiguration.strictMode()) {
                        throw x.syntaxError("Strict mode error: Expected a valid array element");
                    }
                    yield true;
                }
                x.back();
                yield false;
            }
            case ']' -> {
                if (isInitial && jsonParserConfiguration.strictMode() &&
                        x.nextClean() != 0) {
                    throw x.syntaxError("Strict mode error: Unparsed characters found at end of input text");
                }
                yield true;
            }
            default ->
                throw x.syntaxError("Expected a ',' or ']'");
        };
    }

    /**
     * Construct a JSONArray from a source JSON text.
     *
     * @param source
     *            A string that begins with <code>[</code>&nbsp;<small>(left
     *            bracket)</small> and ends with <code>]</code>
     *            &nbsp;<small>(right bracket)</small>.
     */
    public JSONArray(String source) {
        this(source, new JSONTokener.Configuration());
    }

    /**
     * Construct a JSONArray from a source JSON text.
     *
     * @param source
     *            A string that begins with <code>[</code>&nbsp;<small>(left
     *            bracket)</small> and ends with <code>]</code>
     *            &nbsp;<small>(right bracket)</small>.
     * @param jsonParserConfiguration the parser config object
     */
    public JSONArray(String source, JSONTokener.Configuration jsonParserConfiguration) {
        this(new JSONTokener(source, jsonParserConfiguration), jsonParserConfiguration);
    }

    /**
     * Construct a JSONArray from a Collection.
     *
     * @param collection
     *            A Collection.
     */
    public JSONArray(Collection<?> collection) {
      this(collection, 0, new JSONTokener.Configuration());
    }

    /**
     * Construct a JSONArray from a collection with recursion depth.
     *
     * @param collection
     *             A Collection.
     * @param recursionDepth
     *             Variable for tracking the count of nested object creations.
     * @param jsonParserConfiguration
     *             Configuration object for the JSON parser
     */
    JSONArray(Collection<?> collection, int recursionDepth, JSONTokener.Configuration jsonParserConfiguration) {
        if (recursionDepth > jsonParserConfiguration.maxNestingDepth()) {
          throw new IllegalStateException("JSONArray has reached recursion depth limit of " + jsonParserConfiguration.maxNestingDepth());
        }
        if (collection == null) {
            this.myArrayList = new ArrayList<>();
        } else {
            this.myArrayList = new ArrayList<>(collection.size());
            this.myArrayList.ensureCapacity(this.myArrayList.size() + collection.size());
            for (Object o: collection){
                this.put(JSONObject.wrap(o, recursionDepth + 1, jsonParserConfiguration));
            }
        }
    }

    /**
     * Construct a JSONArray with the specified initial capacity.
     *
     * @param initialCapacity
     *            the initial capacity of the JSONArray.
     * @throws IllegalArgumentException
     *             If the initial capacity is negative.
     */
    public JSONArray(int initialCapacity) {
    	if (initialCapacity < 0) {
            throw new IllegalArgumentException(
                    "JSONArray initial capacity cannot be negative.");
    	}
    	this.myArrayList = new ArrayList<>(initialCapacity);
    }

    @Override
    public Iterator<Object> iterator() {
        return this.myArrayList.iterator();
    }

    /**
     * Get the object value associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return An object value.
     * @throws IllegalArgumentException
     *             If there is no value for the index.
     */
    public Object get(int index) {
        Object object = this.opt(index);
        if (object == null) {
            throw new IllegalArgumentException("JSONArray[" + index + "] not found.");
        }
        return object;
    }

    /**
     * Get the boolean value associated with an index. The string values "true"
     * and "false" are converted to boolean.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The truth.
     * @throws IllegalArgumentException
     *             If there is no value for the index or if the value is not
     *             convertible to boolean.
     */
    public boolean getBoolean(int index) {
        Object object = this.get(index);
        if (Boolean.FALSE.equals(object)
                || (object instanceof String s && "false".equalsIgnoreCase(s))) {
            return false;
        } else if (Boolean.TRUE.equals(object)
                || (object instanceof String s && "true".equalsIgnoreCase(s))) {
            return true;
        }
        throw wrongValueFormatException(index, "boolean", object, null);
    }

    /**
     * Get the double value associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     * @throws IllegalArgumentException
     *             If the key is not found or if the value cannot be converted
     *             to a number.
     */
    public double getDouble(int index) {
        final Object object = this.get(index);
        if(object instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(index, "double", object, e);
        }
    }

    /**
     * Get the int value associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     * @throws IllegalArgumentException
     *             If the key is not found or if the value is not a number.
     */
    public int getInt(int index) {
        final Object object = this.get(index);
        if(object instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(index, "int", object, e);
        }
    }

    /**
     * Get the JSONArray associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return A JSONArray value.
     * @throws IllegalArgumentException
     *             If there is no value for the index. or if the value is not a
     *             JSONArray
     */
    public JSONArray getJSONArray(int index) {
        Object object = this.get(index);
        if (object instanceof JSONArray ja) {
            return ja;
        }
        throw wrongValueFormatException(index, "JSONArray", object, null);
    }

    /**
     * Get the JSONObject associated with an index.
     *
     * @param index
     *            subscript
     * @return A JSONObject value.
     * @throws IllegalArgumentException
     *             If there is no value for the index or if the value is not a
     *             JSONObject
     */
    public JSONObject getJSONObject(int index) {
        Object object = this.get(index);
        if (object instanceof JSONObject jo) {
            return jo;
        }
        throw wrongValueFormatException(index, "JSONObject", object, null);
    }

    /**
     * Get the long value associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     * @throws IllegalArgumentException
     *             If the key is not found or if the value cannot be converted
     *             to a number.
     */
    public long getLong(int index) {
        final Object object = this.get(index);
        if(object instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(index, "long", object, e);
        }
    }

    /**
     * Get the string associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return A string value.
     * @throws IllegalArgumentException
     *             If there is no string value for the index.
     */
    public String getString(int index) {
        Object object = this.get(index);
        if (object instanceof String s) {
            return s;
        }
        throw wrongValueFormatException(index, "String", object, null);
    }

    /**
     * Determine if the value is <code>null</code>.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return true if the value at the index is <code>null</code>, or if there is no value.
     */
    public boolean isNull(int index) {
        return JSONObject.NULL.equals(this.opt(index));
    }

    /**
     * Make a string from the contents of this JSONArray. The
     * <code>separator</code> string is inserted between each element. Warning:
     * This method assumes that the data structure is acyclical.
     *
     * @param separator
     *            A string that will be inserted between the elements.
     * @return a string.
     * @throws IllegalArgumentException
     *             If the array contains an invalid number.
     */
    public String join(String separator) {
        int len = this.length();
        if (len == 0) {
            return "";
        }

        var sb = new StringBuilder(
                   JSONObject.valueToString(this.myArrayList.get(0)));

        for (int i = 1; i < len; i++) {
            sb.append(separator)
              .append(JSONObject.valueToString(this.myArrayList.get(i)));
        }
        return sb.toString();
    }

    /**
     * Get the number of elements in the JSONArray, included nulls.
     *
     * @return The length (or size).
     */
    public int length() {
        return this.myArrayList.size();
    }

    /**
     * Removes all of the elements from this JSONArray.
     * The JSONArray will be empty after this call returns.
     */
    public void clear() {
        this.myArrayList.clear();
    }

    /**
     * Get the optional object value associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1. If not, null is returned.
     * @return An object value, or null if there is no object at that index.
     */
    public Object opt(int index) {
        return (index < 0 || index >= this.length()) ? null : this.myArrayList
                .get(index);
    }

    /**
     * Get the optional boolean value associated with an index. It returns false
     * if there is no value at that index, or if the value is not Boolean.TRUE
     * or the String "true".
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The truth.
     */
    public boolean optBoolean(int index) {
        return this.optBoolean(index, false);
    }

    /**
     * Get the optional boolean value associated with an index. It returns the
     * defaultValue if there is no value at that index or if it is not a Boolean
     * or the String "true" or "false" (case insensitive).
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            A boolean default.
     * @return The truth.
     */
    public boolean optBoolean(int index, boolean defaultValue) {
        try {
            return this.getBoolean(index);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get the optional double value associated with an index. NaN is returned
     * if there is no value for the index, or if the value is not a number and
     * cannot be converted to a number.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     */
    public double optDouble(int index) {
        return this.optDouble(index, Double.NaN);
    }

    /**
     * Get the optional double value associated with an index. The defaultValue
     * is returned if there is no value for the index, or if the value is not a
     * number and cannot be converted to a number.
     *
     * @param index
     *            subscript
     * @param defaultValue
     *            The default value.
     * @return The value.
     */
    public double optDouble(int index, double defaultValue) {
        Object val = this.opt(index);
        if (JSONObject.NULL.equals(val)) {
            return defaultValue;
        }
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return JSONObject.stringToNumber(val.toString()).doubleValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get the optional int value associated with an index. Zero is returned if
     * there is no value for the index, or if the value is not a number and
     * cannot be converted to a number.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     */
    public int optInt(int index) {
        return this.optInt(index, 0);
    }

    /**
     * Get the optional int value associated with an index. The defaultValue is
     * returned if there is no value for the index, or if the value is not a
     * number and cannot be converted to a number.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            The default value.
     * @return The value.
     */
    public int optInt(int index, int defaultValue) {
        Object val = this.opt(index);
        if (JSONObject.NULL.equals(val)) {
            return defaultValue;
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        try {
            return JSONObject.stringToNumber(val.toString()).intValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get the optional JSONArray associated with an index. Null is returned if
     * there is no value at that index or if the value is not a JSONArray.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return A JSONArray value.
     */
    public JSONArray optJSONArray(int index) {
        return this.optJSONArray(index, null);
    }

    /**
     * Get the optional JSONArray associated with an index. The defaultValue is returned if
     * there is no value at that index or if the value is not a JSONArray.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            The default.
     * @return A JSONArray value.
     */
    public JSONArray optJSONArray(int index, JSONArray defaultValue) {
        Object object = this.opt(index);
        return object instanceof JSONArray ja ? ja : defaultValue;
    }

    /**
     * Get the optional JSONObject associated with an index. Null is returned if
     * there is no value at that index or if the value is not a JSONObject.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return A JSONObject value.
     */
    public JSONObject optJSONObject(int index) {
        return this.optJSONObject(index, null);
    }

    /**
     * Get the optional JSONObject associated with an index. The defaultValue is returned if
     * there is no value at that index or if the value is not a JSONObject.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            The default.
     * @return A JSONObject value.
     */
    public JSONObject optJSONObject(int index, JSONObject defaultValue) {
        Object object = this.opt(index);
        return object instanceof JSONObject jo ? jo : defaultValue;
    }

    /**
     * Get the optional long value associated with an index. Zero is returned if
     * there is no value for the index, or if the value is not a number and
     * cannot be converted to a number.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     */
    public long optLong(int index) {
        return this.optLong(index, 0);
    }

    /**
     * Get the optional long value associated with an index. The defaultValue is
     * returned if there is no value for the index, or if the value is not a
     * number and cannot be converted to a number.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            The default value.
     * @return The value.
     */
    public long optLong(int index, long defaultValue) {
        Object val = this.opt(index);
        if (JSONObject.NULL.equals(val)) {
            return defaultValue;
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        try {
            return JSONObject.stringToNumber(val.toString()).longValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get the optional string value associated with an index. It returns an
     * empty string if there is no value at that index. If the value is not a
     * string and is not null, then it is converted to a string.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return A String value.
     */
    public String optString(int index) {
        return this.optString(index, "");
    }

    /**
     * Get the optional string associated with an index. The defaultValue is
     * returned if the key is not found.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            The default value.
     * @return A String value.
     */
    public String optString(int index, String defaultValue) {
        Object object = this.opt(index);
        return JSONObject.NULL.equals(object) ? defaultValue : object
                .toString();
    }

    /**
     * Append a boolean value. This increases the array's length by one.
     *
     * @param value
     *            A boolean value.
     * @return this.
     */
    public JSONArray put(boolean value) {
        return this.put(value ? Boolean.TRUE : Boolean.FALSE);
    }

    /**
     * Put a value in the JSONArray, where the value will be a JSONArray which
     * is produced from a Collection.
     *
     * @param value
     *            A Collection value.
     * @return this.
     * @throws IllegalArgumentException
     *            If the value is non-finite number.
     */
    public JSONArray put(Collection<?> value) {
        return this.put(new JSONArray(value));
    }

    /**
     * Append a double value. This increases the array's length by one.
     *
     * @param value
     *            A double value.
     * @return this.
     * @throws IllegalArgumentException
     *             if the value is not finite.
     */
    public JSONArray put(double value) {
        return this.put(Double.valueOf(value));
    }

    /**
     * Append a float value. This increases the array's length by one.
     *
     * @param value
     *            A float value.
     * @return this.
     * @throws IllegalArgumentException
     *             if the value is not finite.
     */
    public JSONArray put(float value) {
        return this.put(Float.valueOf(value));
    }

    /**
     * Append an int value. This increases the array's length by one.
     *
     * @param value
     *            An int value.
     * @return this.
     */
    public JSONArray put(int value) {
        return this.put(Integer.valueOf(value));
    }

    /**
     * Append an long value. This increases the array's length by one.
     *
     * @param value
     *            A long value.
     * @return this.
     */
    public JSONArray put(long value) {
        return this.put(Long.valueOf(value));
    }

    /**
     * Put a value in the JSONArray, where the value will be a JSONObject which
     * is produced from a Map.
     *
     * @param value
     *            A Map value.
     * @return this.
     * @throws IllegalArgumentException
     *            If a value in the map is non-finite number.
     * @throws NullPointerException
     *            If a key in the map is <code>null</code>
     */
    public JSONArray put(Map<?, ?> value) {
        return this.put(new JSONObject(value));
    }

    /**
     * Append an object value. This increases the array's length by one.
     *
     * @param value
     *            An object value. The value should be a Boolean, Double,
     *            Integer, JSONArray, JSONObject, Long, or String, or the
     *            JSONObject.NULL object.
     * @return this.
     * @throws IllegalArgumentException
     *            If the value is non-finite number.
     */
    public JSONArray put(Object value) {
        JSONObject.testValidity(value);
        this.myArrayList.add(value);
        return this;
    }

    /**
     * Put or replace a boolean value in the JSONArray. If the index is greater
     * than the length of the JSONArray, then null elements will be added as
     * necessary to pad it out.
     *
     * @param index
     *            The subscript.
     * @param value
     *            A boolean value.
     * @return this.
     * @throws IllegalArgumentException
     *             If the index is negative.
     */
    public JSONArray put(int index, boolean value) {
        return this.put(index, value ? Boolean.TRUE : Boolean.FALSE);
    }

    /**
     * Put a value in the JSONArray, where the value will be a JSONArray which
     * is produced from a Collection.
     *
     * @param index
     *            The subscript.
     * @param value
     *            A Collection value.
     * @return this.
     * @throws IllegalArgumentException
     *             If the index is negative or if the value is non-finite.
     */
    public JSONArray put(int index, Collection<?> value) {
        return this.put(index, new JSONArray(value));
    }

    /**
     * Put or replace a double value. If the index is greater than the length of
     * the JSONArray, then null elements will be added as necessary to pad it
     * out.
     *
     * @param index
     *            The subscript.
     * @param value
     *            A double value.
     * @return this.
     * @throws IllegalArgumentException
     *             If the index is negative or if the value is non-finite.
     */
    public JSONArray put(int index, double value) {
        return this.put(index, Double.valueOf(value));
    }

    /**
     * Put or replace a float value. If the index is greater than the length of
     * the JSONArray, then null elements will be added as necessary to pad it
     * out.
     *
     * @param index
     *            The subscript.
     * @param value
     *            A float value.
     * @return this.
     * @throws IllegalArgumentException
     *             If the index is negative or if the value is non-finite.
     */
    public JSONArray put(int index, float value) {
        return this.put(index, Float.valueOf(value));
    }

    /**
     * Put or replace an int value. If the index is greater than the length of
     * the JSONArray, then null elements will be added as necessary to pad it
     * out.
     *
     * @param index
     *            The subscript.
     * @param value
     *            An int value.
     * @return this.
     * @throws IllegalArgumentException
     *             If the index is negative.
     */
    public JSONArray put(int index, int value) {
        return this.put(index, Integer.valueOf(value));
    }

    /**
     * Put or replace a long value. If the index is greater than the length of
     * the JSONArray, then null elements will be added as necessary to pad it
     * out.
     *
     * @param index
     *            The subscript.
     * @param value
     *            A long value.
     * @return this.
     * @throws IllegalArgumentException
     *             If the index is negative.
     */
    public JSONArray put(int index, long value) {
        return this.put(index, Long.valueOf(value));
    }

    /**
     * Put a value in the JSONArray, where the value will be a JSONObject that
     * is produced from a Map.
     *
     * @param index
     *            The subscript.
     * @param value
     *            The Map value.
     * @return
     *             reference to self
     * @throws IllegalArgumentException
     *             If the index is negative or if the value is an invalid
     *             number.
     * @throws NullPointerException
     *             If a key in the map is <code>null</code>
     */
    public JSONArray put(int index, Map<?, ?> value) {
        this.put(index, new JSONObject(value, new JSONTokener.Configuration()));
        return this;
    }

    /**
     * Put or replace an object value in the JSONArray. If the index is greater
     * than the length of the JSONArray, then null elements will be added as
     * necessary to pad it out.
     *
     * @param index
     *            The subscript.
     * @param value
     *            The value to put into the array. The value should be a
     *            Boolean, Double, Integer, JSONArray, JSONObject, Long, or
     *            String, or the JSONObject.NULL object.
     * @return this.
     * @throws IllegalArgumentException
     *             If the index is negative or if the value is an invalid
     *             number.
     */
    public JSONArray put(int index, Object value) {
        if (index < 0) {
            throw new IllegalArgumentException("JSONArray[" + index + "] not found.");
        }
        if (index < this.length()) {
            JSONObject.testValidity(value);
            this.myArrayList.set(index, value);
            return this;
        }
        if(index == this.length()){
            // simple append
            return this.put(value);
        }
        // if we are inserting past the length, we want to grow the array all at once
        // instead of incrementally.
        this.myArrayList.ensureCapacity(index + 1);
        while (index != this.length()) {
            // we don't need to test validity of NULL objects
            this.myArrayList.add(JSONObject.NULL);
        }
        return this.put(value);
    }

    /**
     * Remove an index and close the hole.
     *
     * @param index
     *            The index of the element to be removed.
     * @return The value that was associated with the index, or null if there
     *         was no value.
     */
    public Object remove(int index) {
        return index >= 0 && index < this.length()
            ? this.myArrayList.remove(index)
            : null;
    }

    /**
     * Make a JSON text of this JSONArray. For compactness, no unnecessary
     * whitespace is added. If it is not possible to produce a syntactically
     * correct JSON text then null will be returned instead. This could occur if
     * the array contains an invalid number.
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     *
     * @return a printable, displayable, transmittable representation of the
     *         array.
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
     * Make a pretty-printed JSON text of this JSONArray.
     *
     * <p>If <pre> {@code indentFactor > 0}</pre> and the {@link JSONArray} has only
     * one element, then the array will be output on a single line:
     * <pre>{@code [1]}</pre>
     *
     * <p>If an array has 2 or more elements, then it will be output across
     * multiple lines: <pre>{@code
     * [
     * 1,
     * "value 2",
     * 3
     * ]
     * }</pre>
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     *
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @return a printable, displayable, transmittable representation of the
     *         object, beginning with <code>[</code>&nbsp;<small>(left
     *         bracket)</small> and ending with <code>]</code>
     *         &nbsp;<small>(right bracket)</small>.
     */
    public String toString(int indentFactor) {
        int initialSize = myArrayList.size() * 2;
        var sb = new StringBuilder(Math.max(initialSize, 16));
        return this.write(sb, indentFactor, 0).toString();
    }

    /**
     * Write the contents of the JSONArray as JSON text to an Appendable. For
     * compactness, no whitespace is added.
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     *</b>
     * @param writer the appendable object
     * @return The appendable.
     */
    public Appendable write(Appendable writer) {
        return this.write(writer, 0, 0);
    }

    /**
     * Write the contents of the JSONArray as JSON text to an Appendable.
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
            int length = this.length();
            writer.append('[');

            if (length == 1) {
                writeArrayAttempt(writer, indentFactor, indent, 0);
            } else if (length != 0) {
                final int newIndent = indent + indentFactor;

                for (int i = 0; i < length; i += 1) {
                    if (needsComma) {
                        writer.append(',');
                    }
                    if (indentFactor > 0) {
                        writer.append('\n');
                    }
                    JSONObject.indent(writer, newIndent);
                    writeArrayAttempt(writer, indentFactor, newIndent, i);
                    needsComma = true;
                }
                if (indentFactor > 0) {
                    writer.append('\n');
                }
                JSONObject.indent(writer, indent);
            }
            writer.append(']');
            return writer;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeArrayAttempt(Appendable writer, int indentFactor, int indent, int i) {
        try {
            JSONObject.writeValue(writer, this.myArrayList.get(i),
                    indentFactor, indent);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to write JSONArray value at index: " + i, e);
        }
    }

    /**
     * Returns a java.util.List containing all of the elements in this array.
     * If an element in the array is a JSONArray or JSONObject it will also
     * be converted to a List and a Map respectively.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return a java.util.List containing the elements of this array
     */
    public List<Object> toList() {
        List<Object> results = new ArrayList<>(this.myArrayList.size());
        for (Object element : this.myArrayList) {
            if (element == null || JSONObject.NULL.equals(element)) {
                results.add(null);
            } else if (element instanceof JSONArray ja) {
                results.add(ja.toList());
            } else if (element instanceof JSONObject jo) {
                results.add(jo.toMap());
            } else {
                results.add(element);
            }
        }
        return results;
    }

    /**
     * Check if JSONArray is empty.
     *
     * @return true if JSONArray is empty, otherwise false.
     */
    public boolean isEmpty() {
        return this.myArrayList.isEmpty();
    }

    private static IllegalArgumentException wrongValueFormatException(
            int idx,
            String valueType,
            Object value,
            Throwable cause) {
        if(value == null) {
            return new IllegalArgumentException(
                    "JSONArray[" + idx + "] is not a " + valueType + " (null)."
                    , cause);
        }
        if(value instanceof Map || value instanceof Iterable || value instanceof JSONObject) {
            return new IllegalArgumentException(
                    "JSONArray[" + idx + "] is not a " + valueType + " (" + value.getClass() + ")."
                    , cause);
        }
        return new IllegalArgumentException(
                "JSONArray[" + idx + "] is not a " + valueType + " (" + value.getClass() + " : " + value + ")."
                , cause);
    }

}

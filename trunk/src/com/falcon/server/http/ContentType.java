package com.falcon.server.http;


/**
 * Usefull methods for Content-Type processing
 * 
 */
public class ContentType {

    /**
     * Parse the character encoding from the specified content type header.
     * If the content type is null, or there is no explicit character encoding,
     * <code>null</code> is returned.
     *
     * @param contentType a content type header
     */
    public static String getCharsetFromContentType(String contentType) {

        if (contentType == null)
            return (null);
        int start = contentType.indexOf("charset=");
        if (start < 0)
            return (null);
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0)
            encoding = encoding.substring(0, end);
        encoding = encoding.trim();
        if ((encoding.length() > 2) && (encoding.startsWith("\""))
            && (encoding.endsWith("\"")))
            encoding = encoding.substring(1, encoding.length() - 1);
        return (encoding.trim());

    }


    /**
     * Returns true if the given content type contains a charset component,
     * false otherwise.
     *
     * @param type Content type
     * @return true if the given content type contains a charset component,
     * false otherwise
     */
    public static boolean hasCharset(String type) {

        boolean hasCharset = false;

        int len = type.length();
        int index = type.indexOf(';');
        while (index != -1) {
            index++;
            while (index < len && Character.isSpace(type.charAt(index))) {
                index++;
            }
            if (index+8 < len
                    && type.charAt(index) == 'c'
                    && type.charAt(index+1) == 'h'
                    && type.charAt(index+2) == 'a'
                    && type.charAt(index+3) == 'r'
                    && type.charAt(index+4) == 's'
                    && type.charAt(index+5) == 'e'
                    && type.charAt(index+6) == 't'
                    && type.charAt(index+7) == '=') {
                hasCharset = true;
                break;
            }
            index = type.indexOf(';', index);
        }

        return hasCharset;
    }

}

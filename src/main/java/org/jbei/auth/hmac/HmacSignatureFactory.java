/**
 *
 */
package org.jbei.auth.hmac;

import com.google.common.net.PercentEscaper;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpRequestBase;
import org.jbei.auth.KeyTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.*;
import java.util.*;

/**
 * Generates {@link HmacSignature} objects for use in authenticating requests to a REST service. By
 * default this class will generate {@link HmacSignature} objects conforming to version 1 of the
 * JBEI authentication specification. An HTTP Authorization header is set with the format
 * {@code Version:KeyId:UserId:Signature}, with:
 * <ul>
 * <li>{@code Version = 1},</li>
 * <li>{@code KeyId} is a string identifying the key used to sign the request,</li>
 * <li>{@code UserId} is a string identifying the user (if any) the request is submitted on behalf
 * of,</li>
 * <li>{@code Signature} is a Base64-encoded string of the request content signed with the SHA-1
 * HMAC algorithm (specified in RFC 2104)</li>
 * </ul>
 * This class builds objects to generate the {@code Signature} portion of the header, given a
 * request object and a {@code UserId}. The {@code Signature} is generated by constructing a string
 * containing the following separated by a newline character:
 * <ul>
 * <li>{@code UserId}</li>
 * <li>the HTTP Method (e.g. {@code GET}, {@code POST})</li>
 * <li>the HTTP Host</li>
 * <li>the request path</li>
 * <li>the query string, <i>sorted</i> by natural UTF-8 byte ordering of parameter names</li>
 * <li>the content of the request entity body</li>
 * </ul>
 * This constructed string is then signed with the key used to initialize this object.
 *
 * @author wcmorrell
 * @version 1.0
 * @since 1.0
 */
public class HmacSignatureFactory {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Comparator<String> QUERY_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(final String a, final String b) {
            return a.substring(0, a.indexOf("=")).compareTo(b.substring(0, b.indexOf("=")));
        }
    };
    private static final Logger log = LoggerFactory.getLogger(HmacSignatureFactory.class);
    private static final PercentEscaper ESCAPER = new PercentEscaper("-_.~", false);
    private static final String HMAC = "HmacSHA1";
    private static final String NEWLINE = "\n";

    private final KeyTable table;

    /**
     * Convenience method to create a new random key for signing requests.
     *
     * @return a SecretKey instance
     * @throws NoSuchAlgorithmException if the system has no registered security providers able to generate the key
     */
    public static Key createKey() throws NoSuchAlgorithmException {
        final KeyGenerator keyGenerator = KeyGenerator.getInstance(HMAC);
        keyGenerator.init(512, new SecureRandom());
        return keyGenerator.generateKey();
    }

    /**
     * Convenience method to decode a key stored in Base64 encoding.
     *
     * @param encodedKey the encoded key String
     * @return a SecretKey object for the decoded key
     */
    public static Key decodeKey(final String encodedKey) {
        return new SecretKeySpec(Base64.decodeBase64(encodedKey), HMAC);
    }

    /**
     * Convenience method to encode a key to a Base64 String.
     *
     * @param key the key to encode
     * @return a Base64 String representation of the key
     */
    public static String encodeKey(final Key key) {
        return Base64.encodeBase64String(key.getEncoded());
    }

    /**
     * Constructor initializes factory with the secret used to sign requests.
     *
     * @param table object used to look up keys for signing
     */
    public HmacSignatureFactory(final KeyTable table) {
        this.table = table;
    }

    /**
     * @param request a request received via Servlet API
     * @param keyId   the key identifier signing the request
     * @param userId  the user creating the request
     * @return an {@link HmacSignature} initialized with the request headers; the request stream may
     * need to be passed through {@link HmacSignature#filterInput(InputStream)} to calculate
     * the correct signature
     * @throws SignatureException if there is an error setting up the signature
     */
    public HmacSignature buildSignature(final HttpServletRequest request, final String keyId,
                                        final String userId) throws SignatureException {
        try {
            final Mac mac = Mac.getInstance(HMAC);
            final Key key = table.getKey(keyId);
            if (key != null) {
                mac.init(key);
                mac.update((buildRequestString(userId, request)).getBytes(UTF8));
                return new DefaultHmacSignature(mac, userId);
            }
            return null;
        } catch (final InvalidKeyException | NoSuchAlgorithmException e) {
            throw new SignatureException("Failed to initialize signature");
        }
    }

    /**
     * @param request a request to be sent via HttpClient API
     * @param keyId   the key identifier signing the request
     * @param userId  the user creating the request
     * @return an {@link HmacSignature} initialized with the request headers; the request stream may
     * need to be passed through {@link HmacSignature#filterOutput(OutputStream)} to
     * calculate the correct signature
     * @throws SignatureException if there is an error setting up the signature
     */
    public HmacSignature buildSignature(final HttpRequestBase request, final String keyId,
                                        final String userId) throws SignatureException {
        try {
            final Mac mac = Mac.getInstance(HMAC);
            final Key key = table.getKey(keyId);
            if (key != null) {
                mac.init(key);
                mac.update((buildRequestString(userId, request)).getBytes(UTF8));
                return new DefaultHmacSignature(mac, userId);
            }
            return null;
        } catch (final InvalidKeyException | NoSuchAlgorithmException e) {
            throw new SignatureException("Failed to initialize signature");
        }
    }

    /**
     * Builds initial signature object from raw individual components.
     *
     * @param keyId
     * @param userId
     * @param method
     * @param host
     * @param path
     * @param params
     * @return an {@link HmacSignature} initialized with the request headers; the request stream may
     * need to be passed through {@link HmacSignature#filterOutput(OutputStream)} to
     * calculate the correct signature
     * @throws SignatureException
     */
    public HmacSignature buildSignature(final String keyId, final String userId,
                                        final String method, final String host, final String path,
                                        final Map<String, ? extends Iterable<String>> params) throws SignatureException {
        try {
            final Mac mac = Mac.getInstance(HMAC);
            final Key key = table.getKey(keyId);
            if (key != null) {
                mac.init(key);
                mac.update((buildRequestString(userId, method, host, path,
                        extractAndSortParams(params))).getBytes(UTF8));
                return new DefaultHmacSignature(mac, userId);
            }
            return null;
        } catch (final InvalidKeyException | NoSuchAlgorithmException e) {
            throw new SignatureException("Failed to initialize signature");
        }

    }

    private List<String> extractAndSortParams(final Map<String, ? extends Iterable<String>> params) {
        final List<String> encParams = new ArrayList<String>();
        for (final Map.Entry<String, ? extends Iterable<String>> entry : params.entrySet()) {
            final String name = ESCAPER.escape(entry.getKey());
            for (final String value : entry.getValue()) {
                encParams.add(name + "=" + ESCAPER.escape(value));
            }
        }
        Collections.sort(encParams, QUERY_COMPARATOR);
        return encParams;
    }

    private List<String> extractAndSortParams(final HttpServletRequest request) {
        final List<String> encParams = new ArrayList<String>();
        for (final Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            final String name = ESCAPER.escape(entry.getKey());
            for (final String value : entry.getValue()) {
                encParams.add(name + "=" + ESCAPER.escape(value));
            }
        }
        Collections.sort(encParams, QUERY_COMPARATOR);
        return encParams;
    }

    private List<String> extractAndSortParams(final HttpRequestBase request) {
        final List<String> encParams = new ArrayList<String>();
        final String query = request.getURI().getRawQuery();
        if (query != null) {
            // split on ampersand (&)
            for (final String parameter : StringUtils.split(request.getURI().getRawQuery(), "&")) {
                encParams.add(parameter);
            }
        }
        Collections.sort(encParams, QUERY_COMPARATOR);
        return encParams;
    }

    private String buildRequestString(final String userId, final String method, final String host,
                                      final String path, final List<String> params) {
        final StringBuilder buffer = new StringBuilder();
        final String sortedParams = StringUtils.join(params.iterator(), "&");
        buffer.append(userId).append(NEWLINE);
        buffer.append(method).append(NEWLINE);
        buffer.append(host).append(NEWLINE);
        buffer.append(path).append(NEWLINE);
        buffer.append(sortedParams).append(NEWLINE);
        debugRequestString(buffer);
        return buffer.toString();
    }

    private String buildRequestString(final String userId, final HttpServletRequest request) {
        return buildRequestString(userId, request.getMethod(), request.getHeader("Host"),
                request.getRequestURI(), extractAndSortParams(request));
    }

    private String buildRequestString(final String userId, final HttpRequestBase request) {
        final Header host = request.getFirstHeader("Host");
        final URI uri = request.getURI();
        return buildRequestString(userId, request.getMethod(), host.getValue(), uri.getRawPath(),
                extractAndSortParams(request));
    }

    private void debugRequestString(final StringBuilder buffer) {
        if (log.isDebugEnabled()) {
            final StringBuilder debug = new StringBuilder();
            debug.append("Constructed request string:").append(NEWLINE);
            debug.append("-----BEGIN-----").append(NEWLINE);
            debug.append(buffer).append(NEWLINE);
            debug.append("----- END -----").append(NEWLINE);
            log.debug(debug.toString());
        }
    }

}

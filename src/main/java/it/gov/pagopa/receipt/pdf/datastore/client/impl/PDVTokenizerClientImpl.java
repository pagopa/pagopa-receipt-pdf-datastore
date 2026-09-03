package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import it.gov.pagopa.receipt.pdf.datastore.client.PDVTokenizerClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.*;

/**
 * {@inheritDoc}
 */
public class PDVTokenizerClientImpl implements PDVTokenizerClient {

    private final Logger logger = LoggerFactory.getLogger(PDVTokenizerClientImpl.class);

    private static final String BASE_PATH = System.getenv().getOrDefault("PDV_TOKENIZER_BASE_PATH", "https://api.uat.tokenizer.pdv.pagopa.it/tokenizer/v1");
    private static final String SUBSCRIPTION_KEY = System.getenv().getOrDefault("PDV_TOKENIZER_SUBSCRIPTION_KEY", "");
    private static final String SUBSCRIPTION_KEY_HEADER = System.getenv().getOrDefault("TOKENIZER_APIM_HEADER_KEY", "x-api-key");
    private static final String SEARCH_TOKEN_ENDPOINT = System.getenv().getOrDefault("PDV_TOKENIZER_SEARCH_TOKEN_ENDPOINT", "/tokens/search");
    private static final String FIND_PII_ENDPOINT = System.getenv().getOrDefault("PDV_TOKENIZER_FIND_PII_ENDPOINT", "/tokens/%s/pii");
    private static final String CREATE_TOKEN_ENDPOINT = System.getenv().getOrDefault("PDV_TOKENIZER_CREATE_TOKEN_ENDPOINT", "/tokens");

    private final HttpClient client;

    private PDVTokenizerClientImpl() {
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    PDVTokenizerClientImpl(HttpClient client) {
        this.client = client;
    }

    public static PDVTokenizerClientImpl getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * Bill Pugh singleton holder: the JVM guarantees that the class is loaded
     * (and therefore INSTANCE initialized) lazily and in a thread-safe way.
     */
    private static class SingletonHelper {
        private static final PDVTokenizerClientImpl INSTANCE = new PDVTokenizerClientImpl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpResponse<String> searchTokenByPII(String piiBody) throws PDVTokenizerException {
        String uri = String.format("%s%s", BASE_PATH, SEARCH_TOKEN_ENDPOINT);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .version(HttpClient.Version.HTTP_2)
                .header(SUBSCRIPTION_KEY_HEADER, SUBSCRIPTION_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(piiBody))
                .build();

        return makeCall(request, MSG_PDV_SEARCHED_TOKEN, PATH_PDV_SEARCH_TOKEN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpResponse<String> findPIIByToken(String token) throws PDVTokenizerException {
        String endpoint = String.format(FIND_PII_ENDPOINT, token);
        String uri = String.format("%s%s", BASE_PATH, endpoint);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .version(HttpClient.Version.HTTP_2)
                .header(SUBSCRIPTION_KEY_HEADER, SUBSCRIPTION_KEY)
                .build();

        return makeCall(request, MSG_PDV_FETCHED_PII, PATH_PDV_FIND_PII);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpResponse<String> createToken(String piiBody) throws PDVTokenizerException {
        String uri = String.format("%s%s", BASE_PATH, CREATE_TOKEN_ENDPOINT);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .version(HttpClient.Version.HTTP_2)
                .header(SUBSCRIPTION_KEY_HEADER, SUBSCRIPTION_KEY)
                .PUT(HttpRequest.BodyPublishers.ofString(piiBody))
                .build();

        return makeCall(request, MSG_PDV_CREATED_TOKEN, PATH_PDV_CREATE_TOKEN);
    }

    private HttpResponse<String> makeCall(
            HttpRequest request,
            String message,
            String path
    ) throws PDVTokenizerException {
        long startNanos = System.nanoTime();
        try {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            logIoSuccess(logger, message,
                    DEP_PDV_TOKENIZER, path, startNanos,
                    Map.of(DETAILS_STATUS_CODE, resp.statusCode()));
            return resp;
        } catch (IOException e) {
            logIoFailure(logger, "I/O error when invoking PDV Tokenizer", DEP_PDV_TOKENIZER, path, startNanos, e, null);
            throw new PDVTokenizerException("I/O error when invoking PDV Tokenizer", ReasonErrorCode.ERROR_PDV_IO.getCode(), e);
        } catch (InterruptedException e) {
            logIoFailure(logger, "Unexpected error when invoking PDV Tokenizer, the thread was interrupted", DEP_PDV_TOKENIZER, path, startNanos, e, null);
            Thread.currentThread().interrupt();
            throw new PDVTokenizerException("Unexpected error when invoking PDV Tokenizer, the thread was interrupted", ReasonErrorCode.ERROR_PDV_UNEXPECTED.getCode(), e);
        }
    }
}

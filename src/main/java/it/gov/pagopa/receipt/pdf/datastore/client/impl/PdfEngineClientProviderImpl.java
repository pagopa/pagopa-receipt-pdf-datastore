package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import feign.Feign;
import feign.Logger;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;
import feign.okhttp.OkHttpClient;
import feign.slf4j.Slf4jLogger;
import it.gov.pagopa.receipt.pdf.datastore.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.datastore.client.PdfEngineClientProvider;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PdfEngineClientProviderImpl implements PdfEngineClientProvider {

    private final String pdfEngineEndpoint = System.getenv("PDF_ENGINE_ENDPOINT");

    public PdfEngineClient provideClient(){

        return Feign.builder()
                .client(new OkHttpClient())
                .encoder(new GsonEncoder())
                .decoder(new GsonDecoder())
                .logger(new Slf4jLogger())
                .logLevel(Logger.Level.FULL)
                .target(PdfEngineClient.class, pdfEngineEndpoint);
    }
}

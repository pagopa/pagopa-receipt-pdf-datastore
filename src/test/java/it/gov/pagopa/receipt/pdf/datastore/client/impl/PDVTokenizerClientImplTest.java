package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import it.gov.pagopa.receipt.pdf.datastore.client.PDVTokenizerClient;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PDVTokenizerClientImplTest {

    private HttpClient clientMock;
    private PDVTokenizerClient sut;
    private HttpResponse<String> responseMock;

    @BeforeEach
    void setUp() {
        clientMock = mock(HttpClient.class);
        sut = spy(new PDVTokenizerClientImpl(clientMock));
        responseMock = mock(HttpResponse.class);
    }

    @Test
    void searchTokenByPIISuccess() throws PDVTokenizerException, IOException, InterruptedException {
        doReturn(responseMock).when(clientMock).send(any(), any());
        doReturn(200).when(responseMock).statusCode();
        sut.searchTokenByPII(anyString());

        verify(clientMock).send(any(), any());
    }

    @Test
    void findPIIByTokenSuccess() throws PDVTokenizerException, IOException, InterruptedException {
        doReturn(responseMock).when(clientMock).send(any(), any());
        doReturn(200).when(responseMock).statusCode();
        sut.findPIIByToken(anyString());

        verify(clientMock).send(any(), any());
    }

    @Test
    void createTokenSuccess() throws PDVTokenizerException, IOException, InterruptedException {
        doReturn(responseMock).when(clientMock).send(any(), any());
        doReturn(201).when(responseMock).statusCode();
        sut.createToken(anyString());

        verify(clientMock).send(any(), any());
    }

    @Test
    void searchTokenByPIIFailThrowsIOException() throws IOException, InterruptedException {
        doThrow(IOException.class).when(clientMock).send(any(), any());

        assertThrows(PDVTokenizerException.class, () -> sut.searchTokenByPII(anyString()));

        verify(clientMock).send(any(), any());
    }

    @Test
    void searchTokenByPIIFailThrowsInterruptedException() throws IOException, InterruptedException {
        doThrow(InterruptedException.class).when(clientMock).send(any(), any());

        assertThrows(PDVTokenizerException.class, () -> sut.searchTokenByPII(anyString()));

        verify(clientMock).send(any(), any());
    }

    @Test
    void findPIIByTokenFailThrowsIOException() throws IOException, InterruptedException {
        doThrow(IOException.class).when(clientMock).send(any(), any());

        assertThrows(PDVTokenizerException.class, () -> sut.findPIIByToken(anyString()));

        verify(clientMock).send(any(), any());
    }

    @Test
    void findPIIByTokenFailThrowsInterruptedException() throws IOException, InterruptedException {
        doThrow(InterruptedException.class).when(clientMock).send(any(), any());

        assertThrows(PDVTokenizerException.class, () -> sut.findPIIByToken(anyString()));

        verify(clientMock).send(any(), any());
    }

    @Test
    void createTokenFailThrowsIOException() throws IOException, InterruptedException {
        doThrow(IOException.class).when(clientMock).send(any(), any());

        assertThrows(PDVTokenizerException.class, () -> sut.createToken(anyString()));

        verify(clientMock).send(any(), any());
    }

    @Test
    void createTokenFailThrowsInterruptedException() throws IOException, InterruptedException {
        doThrow(InterruptedException.class).when(clientMock).send(any(), any());

        assertThrows(PDVTokenizerException.class, () -> sut.createToken(anyString()));

        verify(clientMock).send(any(), any());
    }
}
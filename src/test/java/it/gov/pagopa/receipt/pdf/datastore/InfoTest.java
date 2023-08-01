package it.gov.pagopa.receipt.pdf.datastore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.logging.Logger;

import it.gov.pagopa.receipt.pdf.datastore.model.AppInfo;
import it.gov.pagopa.receipt.pdf.datastore.util.HttpResponseMessageMock;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class InfoTest {

    @Mock
    ExecutionContext executionContextMock;

    @Spy
    Info sut;

    @Test
    void runOK() {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, executionContextMock);

        // test assertion
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @SneakyThrows
    @Test
    void getInfoOk() {

        // Mocking service creation
        Logger logger = Logger.getLogger("example-test-logger");
        String path = "/META-INF/maven/it.gov.pagopa.receipt.pdf.datastore/receipt-pdf-datastore/pom.properties";

        // Execute function
        AppInfo response = sut.getInfo(logger, path);

        // Checking assertions
        assertNotNull(response.getName());
        assertNotNull(response.getVersion());
        assertNotNull(response.getEnvironment());
    }

    @SneakyThrows
    @Test
    void getInfoKo() {

        // Mocking service creation
        Logger logger = Logger.getLogger("example-test-logger");
        String path = "/META-INF/maven/it.gov.pagopa.receipt.pdf.datastore/receipt-pdf-datastore/fake";

        // Execute function
        AppInfo response = sut.getInfo(logger, path);

        // Checking assertions
        assertNull(response.getName());
        assertNull(response.getVersion());
        assertNotNull(response.getEnvironment());
    }

}

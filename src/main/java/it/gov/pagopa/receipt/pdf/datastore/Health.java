package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;


/**
 * Azure Functions with Azure Http trigger.
 */
public class Health {

    /**
     * This function will be invoked when a Http Trigger occurs
     *
     * @return response with HttpStatus.OK
     */
    @FunctionName("Health")
    public HttpResponseMessage run(
            @HttpTrigger(name = "HealthTrigger",
                    methods = {HttpMethod.GET},
                    route = "health",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        return request.createResponseBuilder(HttpStatus.OK)
                .build();
    }
}
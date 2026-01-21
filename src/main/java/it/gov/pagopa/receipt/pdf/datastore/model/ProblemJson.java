package it.gov.pagopa.receipt.pdf.datastore.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * Object returned as response in case of an error.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProblemJson {

    @JsonProperty("title")
    private String title;

    @JsonProperty("status")
    @Min(100)
    @Max(600)
    private Integer status;

    @JsonProperty("detail")
    private String detail;

}
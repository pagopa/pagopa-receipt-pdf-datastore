package it.gov.pagopa.receipt.pdf.datastore.service;

import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventUnprocessableEntityException;

import java.util.List;

public interface HelpdeskService {
    void validateCartBizEvents(List<BizEvent> bizEvents) throws BizEventBadRequestException, BizEventUnprocessableEntityException;

}

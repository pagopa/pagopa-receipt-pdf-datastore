package it.gov.pagopa.receipt.pdf.datastore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventUnprocessableEntityException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveCartRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveRecoverResult;

import java.util.List;

/**
 * Service that hold receipt helpdesk logic
 */
public interface HelpdeskService {

    /**
     * Recover the specified receipt.
     * <p>
     * It retrieves the related biz event, rebuild {@link Receipt} model, saves it and sends message on queue
     * for PDF generation
     * </p>
     *
     * @param existingReceipt the receipt to recover
     * @return the recover receipt
     * @throws BizEventUnprocessableEntityException in case the biz event does not have the correct total notice
     * @throws BizEventBadRequestException          in case the biz event is invalid for receipt generation
     * @throws BizEventNotFoundException            in case no biz event is found for the specified receipt
     */
    Receipt recoverFailedReceipt(Receipt existingReceipt)
            throws BizEventUnprocessableEntityException, BizEventBadRequestException, BizEventNotFoundException;

    /**
     * Recover the specified cart.
     * <p>
     * It retrieves all related biz event, rebuild {@link CartForReceipt} model, saves it and sends message on queue
     * for PDF generation
     * </p>
     *
     * @param existingCart the cart to recover
     * @return the recover cart
     * @throws BizEventUnprocessableEntityException in case one of the biz events does not have the correct total notice
     * @throws BizEventBadRequestException          in case one of the biz events is invalid for receipt generation
     * @throws PDVTokenizerException                in case an error occur while tokenizing PII data
     * @throws JsonProcessingException              in case an error occur while parsing tokenizer response
     */
    CartForReceipt recoverFailedCart(CartForReceipt existingCart)
            throws BizEventUnprocessableEntityException, BizEventBadRequestException, PDVTokenizerException, JsonProcessingException;

    /**
     * Reset notification info and set status to {@link ReceiptStatusType#GENERATED} in order to trigger notification
     * process
     *
     * @param receipt the receipt to reset
     * @return the updated receipt
     */
    Receipt recoverNoNotifiedReceipt(Receipt receipt);

    /**
     * Reset notification info and set status to {@link CartStatusType#GENERATED} in order to trigger notification
     * process
     *
     * @param cart the cart to reset
     * @return the updated cart
     */
    CartForReceipt recoverNoNotifiedCart(CartForReceipt cart);

    /**
     * Massive recover all failed receipt with the specified status {@link ReceiptStatusType}
     *
     * @param status the status to be recovered
     * @return the recover result
     */
    MassiveRecoverResult massiveRecoverByStatus(ReceiptStatusType status);

    /**
     * Massive recover all failed cart with the specified status {@link CartStatusType}
     *
     * @param status the status to be recovered
     * @return the recover result
     */
    MassiveCartRecoverResult massiveRecoverByStatus(CartStatusType status);

    /**
     * Massive recover all not notified receipt with the specified status {@link ReceiptStatusType}
     *
     * @param status the status to be recovered
     * @return the recover result
     */
    List<Receipt> massiveRecoverNoNotified(ReceiptStatusType status);

    /**
     * Massive recover all not notified cart receipt with the specified status {@link CartStatusType}
     *
     * @param status the status to be recovered
     * @return the recover result
     */
    List<CartForReceipt> massiveRecoverNoNotified(CartStatusType status);
}

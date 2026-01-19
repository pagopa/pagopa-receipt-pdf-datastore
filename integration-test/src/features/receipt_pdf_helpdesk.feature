Feature: All about payment events to recover managed by Azure functions receipt-pdf-helpdesk

  Scenario: receiptToReviewed API retrieve a receipt error and updates its status to REVIEWED
    Given a receipt-error with bizEventId "receipt-datastore-helpdesk-int-test-id-1" and status "TO_REVIEW" stored into receipt-error datastore
    When receiptToReviewed API is called with bizEventId "receipt-datastore-helpdesk-int-test-id-1"
    Then the api response has a 200 Http status
    And the receipt-error with bizEventId "receipt-datastore-helpdesk-int-test-id-1" is recovered from datastore
    And the receipt-error has not status "TO_REVIEW"

  Scenario: recoverFailedReceipt API retrieve a receipt in status FAILED and updates its status
    Given a receipt with eventId "receipt-datastore-helpdesk-int-test-id-2" and status "FAILED" stored into receipt datastore
    And a biz event with id "receipt-datastore-helpdesk-int-test-id-2" and status "DONE" stored on biz-events datastore
    When recoverFailedReceipt API is called with eventId "receipt-datastore-helpdesk-int-test-id-2"
    Then the api response has a 200 Http status
    And the receipt with eventId "receipt-datastore-helpdesk-int-test-id-2" is recovered from datastore
    And the receipt has not status "FAILED"

  Scenario: recoverFailedReceiptMassive API retrieve all the receipts in status FAILED and updates their status
    Given a list of 5 receipts in status "FAILED" stored into receipt datastore starting from eventId "receipt-datastore-helpdesk-int-test-id-3"
    And a list of 5 biz events in status "DONE" stored into biz-events datastore starting from eventId "receipt-datastore-helpdesk-int-test-id-3"
    When recoverFailedReceiptMassive API is called with status "FAILED" as query param
    Then the api response has a 200 Http status
    And the list of receipt is recovered from datastore and no receipt in the list has status "FAILED"

  Scenario: recoverNotNotifiedReceipt API retrieve a receipt in status IO_ERROR_TO_NOTIFY and updates its status
    Given a receipt with eventId "receipt-datastore-helpdesk-int-test-id-4" and status "IO_ERROR_TO_NOTIFY" stored into receipt datastore
    And a biz event with id "receipt-datastore-helpdesk-int-test-id-4" and status "DONE" stored on biz-events datastore
    When recoverNotNotifiedReceipt API is called with eventId "receipt-datastore-helpdesk-int-test-id-4"
    Then the api response has a 200 Http status
    And the receipt with eventId "receipt-datastore-helpdesk-int-test-id-4" is recovered from datastore
    And the receipt has not status "IO_ERROR_TO_NOTIFY"

  Scenario: recoverNotNotifiedReceiptMassive API retrieve all the receipts in status IO_ERROR_TO_NOTIFY and updates their status
    Given a list of 5 receipts in status "IO_ERROR_TO_NOTIFY" stored into receipt datastore starting from eventId "receipt-datastore-helpdesk-int-test-id-5"
    And a list of 5 biz events in status "DONE" stored into biz-events datastore starting from eventId "receipt-datastore-helpdesk-int-test-id-5"
    When recoverNotNotifiedReceiptMassive API is called with status "IO_ERROR_TO_NOTIFY" as query param
    Then the api response has a 200 Http status
    And the list of receipt is recovered from datastore and no receipt in the list has status "IO_ERROR_TO_NOTIFY"

  Scenario: cartReceiptToReviewed API retrieve a cart receipt error and updates its status to REVIEWED
    Given a cart-receipt-error with cartId "receipt-datastore-helpdesk-int-test-id-6" and status "TO_REVIEW" stored into cart-receipt-error datastore
    When cartReceiptToReviewed API is called with cartId "receipt-datastore-helpdesk-int-test-id-6"
    Then the api response has a 200 Http status
    And the cart-receipt-error with cartId "receipt-datastore-helpdesk-int-test-id-6" is recovered from datastore
    And the cart-receipt-error has not status "TO_REVIEW"
  
  Scenario: recoverFailedCartReceipt API retrieve a cart receipt in status FAILED and updates its status
    Given a cart receipt with cartId "receipt-datastore-helpdesk-int-test-id-7" and status "FAILED" stored into receipt datastore
    And a list of 2 biz events of a cart stored into biz-events datastore starting from eventId "receipt-datastore-helpdesk-int-test-id-7"
    When recoverFailedCartReceipt API is called with cartId "receipt-datastore-helpdesk-int-test-id-7"
    Then the api response has a 200 Http status
    And the cart receipt with cartId "receipt-datastore-helpdesk-int-test-id-7" is recovered from datastore
    And the cart receipt has not status "FAILED"
    
  Scenario: recoverFailedCartReceiptMassive API retrieve all the cart receipts in status FAILED and updates their status
    Given a list of 5 cart receipts in status "FAILED" stored into cart receipt datastore starting from eventId "receipt-datastore-helpdesk-int-test-id-8"
    And a list of 2 biz events for each cart is stored into biz-events datastore
    When recoverFailedCartReceiptMassive API is called with status "FAILED" as query param
    Then the api response has a 200 Http status
    And the list of cart receipt is recovered from datastore and no cart receipt in the list has status "FAILED"

  Scenario: recoverNotNotifiedCartReceipt API retrieve a cart receipt in status IO_ERROR_TO_NOTIFY and updates its status
    Given a cart receipt with cartId "receipt-datastore-helpdesk-int-test-id-9" and status "IO_ERROR_TO_NOTIFY" stored into receipt datastore
    And a list of 2 biz events of a cart stored into biz-events datastore starting from eventId "receipt-datastore-helpdesk-int-test-id-9"
    When recoverNotNotifiedCartReceipt API is called with eventId "receipt-datastore-helpdesk-int-test-id-9"
    Then the api response has a 200 Http status
    And the cart receipt with cartId "receipt-datastore-helpdesk-int-test-id-9" is recovered from datastore
    And the cart receipt has not status "IO_ERROR_TO_NOTIFY"
    
  Scenario: recoverNotNotifiedCartReceiptMassive API retrieve all the cart receipts in status IO_ERROR_TO_NOTIFY and updates their status
    Given a list of 5 cart receipts in status "IO_ERROR_TO_NOTIFY" stored into cart receipt datastore starting from eventId "receipt-datastore-helpdesk-int-test-id-10"
    And a list of 2 biz events of a cart stored into biz-events datastore starting from eventId "receipt-datastore-helpdesk-int-test-id-10"
    When recoverNotNotifiedCartReceiptMassive API is called with status "IO_ERROR_TO_NOTIFY" as query param
    Then the api response has a 200 Http status
    And the list of cart receipt is recovered from datastore and no cart receipt in the list has status "IO_ERROR_TO_NOTIFY"
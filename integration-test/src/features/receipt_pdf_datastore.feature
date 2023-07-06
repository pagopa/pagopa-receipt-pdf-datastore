Feature: All about payment events consumed by Azure functions receipt-pdf-datastore

  Scenario: a biz event stored on biz-events datastore is stored into receipts datastore
    Given a random biz event with id "receipt-datastore-int-test-id-1" stored on biz-events datastore with status DONE
    When biz event has been properly stored into receipt datastore after 10000 ms with eventId "receipt-datastore-int-test-id-1"
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-datastore-int-test-id-1"
    And the receipt has not the status "NOT_QUEUE_SENT"

  Scenario: a biz event enqueued on receipts queue trigger the PDF receipt generation that is stored on receipts datastore and blob storage
    Given a receipt with id "receipt-datastore-int-test-id-2" stored into receipt datastore
    And a random biz event with id "receipt-datastore-int-test-id-2" enqueued on receipts queue
    When the PDF receipt has been properly generate from biz event after 20000 ms
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-datastore-int-test-id-2"
    And the receipt has not the status "NOT_QUEUE_SENT"
    And the receipt has not the status "INSERTED"
    And the blob storage has the PDF document

  Scenario: a biz event enqueued on receipts poison queue is enqueued on receipt queue that trigger the PDF receipt generation
    Given a receipt with id "receipt-datastore-int-test-id-3" stored into receipt datastore
    And a random biz event with id "receipt-datastore-int-test-id-3" enqueued on receipts poison queue with poison retry "false"
    When the PDF receipt has been properly generate from biz event after 20000 ms
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-datastore-int-test-id-3"
    And the receipt has not the status "NOT_QUEUE_SENT"
    And the receipt has not the status "INSERTED"
    And the blob storage has the PDF document

  Scenario: a biz event enqueued on receipts poison queue is stored on receipt-message-error datastore
    Given a random biz event with id "receipt-datastore-int-test-id-4" enqueued on receipts poison queue with poison retry "true"
    When the biz event has been properly stored on receipt-message-error datastore after 20000 ms
    Then the receipt-message-error datastore returns the error receipt
    And the error receipt has the status "TO_REVIEW"

  Scenario: a biz event stored on receipt-message-error datastore is enqueued on receipt queue that trigger the PDF receipt generation
    Given a receipt with id "receipt-datastore-int-test-id-5" stored into receipt datastore
    And a error receipt with id "receipt-datastore-int-test-id-5" stored into receipt-message-error datastore with status REVIEWED
    When the PDF receipt has been properly generate from biz event after 20000 ms
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-datastore-int-test-id-5"
    And the receipt has not the status "NOT_QUEUE_SENT"
    And the receipt has not the status "INSERTED"
    And the blob storage has the PDF document

Feature: All about payment events consumed by Azure functions receipt-pdf-datastore

  Scenario: a biz event stored on biz-events datastore is stored into receipts datastore
    Given a random biz event with id "receipt-datastore-test-id-1" stored on biz-events datastore with status "DONE"
    When biz event has been properly stored into receipt datastore after 10000 ms with eventId "receipt-datastore-test-id-1"
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-datastore-test-id-1"
    And the receipt has not the status "NOT_QUEUE_SENT"

  Scenario: a biz event enqueued on receipts queue trigger the PDF receipt generation that is stored on receipts datastore and blob storage
    Given a receipt with id "receipt-datastore-test-id-2" stored into receipt datastore
    And a random biz event with id "receipt-datastore-test-id-2" enqueued on receipts queue
    When the PDF receipt has been properly generate from biz event after 15000 ms
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-datastore-test-id-2"
    And the receipt has the status "GENERATED"
    And the blob storage has the PDF document

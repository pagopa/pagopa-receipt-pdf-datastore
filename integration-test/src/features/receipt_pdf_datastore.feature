Feature: All about payment events consumed by Azure functions receipt-pdf-datastore

  Scenario: a biz event stored on biz-events datastore is stored into receipts datastore
    Given a random biz event with id "receipt-datastore-int-test-id-1" stored on biz-events datastore with status DONE
    When biz event has been properly stored into receipt datastore after 10000 ms with eventId "receipt-datastore-int-test-id-1"
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-datastore-int-test-id-1"
    And the receipt has not the status "NOT_QUEUE_SENT"
    And the receipt has not the status "FAILED"

  Scenario: a list of biz event from the same cart stored on biz-events datastore is stored into receipts datastore
    Given a list of 5 bizEvents starting with id "receipt-datastore-int-test-id-2" and transactionId "receipt-datastore-int-test-transactionId-2" stored on biz-events datastore with status DONE
    And a cart event with id "receipt-datastore-int-test-transactionId-2" containing the ids the bizEvents
    When biz event has been properly stored into receipt datastore after 10000 ms with eventId "receipt-datastore-int-test-transactionId-2"
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-datastore-int-test-transactionId-2"
    And the receipt has not the status "NOT_QUEUE_SENT"
    And the receipt has not the status "FAILED"
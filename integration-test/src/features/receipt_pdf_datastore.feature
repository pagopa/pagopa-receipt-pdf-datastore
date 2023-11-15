Feature: All about payment events consumed by Azure functions receipt-pdf-datastore

  Scenario: a biz event stored on biz-events datastore is stored into receipts datastore
    Given a random biz event with id "receipt-datastore-int-test-id-1" stored on biz-events datastore with status DONE
    When biz event has been properly stored into receipt datastore after 10000 ms with eventId "receipt-datastore-int-test-id-1"
    Then the receipts datastore returns the receipt
    And the receipt has eventId "receipt-datastore-int-test-id-1"
    And the receipt has not the status "NOT_QUEUE_SENT"

    Given a random receipt with id "receipt-datastore-int-test-id-1" stored with status FAILED
    When HTTP recovery request is called
    Then response has a 200 Http status
    And the receipt has not the status "FAILED" after 10000 ms

    Given a random receipt with id "receipt-datastore-int-test-id-1" stored with status FAILED
    When HTTP recovery request is called without eventId
    Then response has a 200 Http status
    And the receipt has not the status "FAILED" after 10000 ms
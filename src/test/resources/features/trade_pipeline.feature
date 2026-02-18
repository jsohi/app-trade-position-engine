Feature: Trade pipeline processing

  Background:
    Given the Aeron IPC pipeline is started

  Scenario: Random trades pipeline
    When 1000 random trades are sent
    Then all trades are received and aggregated

  Scenario: Known buy trade produces correct position
    When a buy trade is sent for account 1 security 1 with quantity 500 and price 100.0
    Then the position for account 1 security 1 has buy quantity 500
    And the position for account 1 security 1 has sell quantity 0

  Scenario: Known buy and sell trades produce correct net position
    When a buy trade is sent for account 2 security 5 with quantity 300 and price 50.0
    And a sell trade is sent for account 2 security 5 with quantity 100 and price 60.0
    Then the position for account 2 security 5 has buy quantity 300
    And the position for account 2 security 5 has sell quantity 100
    And the position for account 2 security 5 has net quantity 200

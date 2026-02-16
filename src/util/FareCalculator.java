package util;

/**
 * SCD Concept: Refactoring (Business Logic Separation)
 * Handles the business logic for calculating ride fares.
 */
public class FareCalculator {
    private static final double BASE_FARE = 300.0;
    private static final double FARE_PER_PASSENGER = 150.0;
    public static double calculateRevenue(int passengerCount) {
        if (passengerCount == 0)
            return 0.0;
        // Total Revenue = Base cost + (cost per person * count)
        return BASE_FARE + (passengerCount * FARE_PER_PASSENGER);
    }
}

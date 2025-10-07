package com.ignacioramirez.itemDetailService.domain;

public record Rating(double average, int count) {
    public Rating {
        if (count < 0) throw new IllegalArgumentException("count >= 0");
        if (count == 0) {
            if (Double.compare(average, 0.0) != 0)
                throw new IllegalArgumentException("average must be 0.0 when count == 0");
        } else {
            if (average < 1.0 || average > 5.0)
                throw new IllegalArgumentException("average must be between 1.0 and 5.0 when count > 0");
        }
    }

    public static Rating empty() { return new Rating(0.0, 0); }

    public Rating addVote(int stars) {
        if (stars < 1 || stars > 5) throw new IllegalArgumentException("stars 1..5");
        double newAvg = (average * count + stars) / (count + 1.0);
        return new Rating(newAvg, count + 1);
    }
}

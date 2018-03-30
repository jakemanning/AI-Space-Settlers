package capp7507;

class MovementUtil {
    static double angleDifference(double angle1, double angle2) {
        double difference = Math.abs(angle1 - angle2);
        double wrappedAngle1 = angle1 < 0 ? 2 * Math.PI + angle1 : angle1;
        double wrappedAngle2 = angle2 < 0 ? 2 * Math.PI + angle2 : angle2;
        double wrappedDifference = Math.abs(wrappedAngle1 - wrappedAngle2);
        return Math.min(difference, wrappedDifference);
    }
}

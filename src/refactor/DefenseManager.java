package refactor;

import ai.abstraction.pathfinding.PathFinding;
import rts.UnitAction;
import rts.units.Unit;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class DefenseManager extends HeuristicsManager {

    // Defense Modes **************************************************************************************************
    public static final int DEFEND_BASE = 0; // Attack units closest to base.
    public static final int DEFEND_SELF = 1; // Attack units closest to self.

    PathFinding pathFinder;

    public DefenseManager(StateMonitor stateMonitor, PathFinding pathFinder, Unit unit, List<UnitAction> unitActions) {
        super(stateMonitor, unit, unitActions);
        this.pathFinder = pathFinder;
    }

    /**
     * Filter unit-actions of a defense unit.
     * Attack actions are kept, if any. If the unit can move, its movement is restricted to a predefined defense perimeter
     * either rectangular, or circular. Depending on the defense mode, it will either chase units close to base, or close
     * to self. If no perimeter is defined, it will follow the mode's behaviour.
     *
     * @param horizontalDistance The horizontal distance from base of the rectangular defense perimeter.
     * @param verticalDistance The vertical distance from base of the rectangular defense perimeter.
     * @param radius The distance from base of the circular defense perimeter.
     * @param maxTargets The maximum number of enemy targets.
     * @param defenseMode
     * @param epsilonDefenseMovement
     * @param shuffleActions
     * @return Filtered unit-actions list.
     */
    public List<UnitAction> filterActions(int horizontalDistance, int verticalDistance, int radius, int maxTargets,
                                          int defenseMode, float epsilonDefenseMovement, boolean shuffleActions) {

        List<UnitAction> filteredActions = new LinkedList<>(attackActions); // Attack actions are always added.

        if (!moveActions.isEmpty()) { // Movement is possible.

            if (random.nextFloat() >= epsilonDefenseMovement) { // Exploit
                switch (defenseMode) {
                    case DEFEND_BASE: // Chase opponent units inside defense perimeter.
                        if (horizontalDistance > 0 && verticalDistance > 0) // Rectangular Defense Perimeter.
                            // Restrict movement around the predefined perimeter around the base. Chase units inside the perimeter.
                            filteredActions.addAll(
                                    keepInsideRectangularPerimeterAroundBase(unit,
                                            getMoveActionsToOpponentUnitsClosestToBase(unit, maxTargets),
                                            horizontalDistance, verticalDistance));
                        else if (radius > 0) // Circular Defense Perimeter.
                            filteredActions.addAll(
                                    keepInsideCircularPerimeterAroundBase(unit,
                                            getMoveActionsToOpponentUnitsClosestToBase(unit, maxTargets),
                                            radius));
                        else // No perimeter, chase units close to the base.
                            filteredActions.addAll(getMoveActionsToOpponentUnitsClosestToBase(unit, maxTargets));
                        break;

                    case DEFEND_SELF: // Chase the opponent units closest to self.
                        if (horizontalDistance > 0 && verticalDistance > 0)
                            filteredActions.addAll(
                                    keepInsideRectangularPerimeterAroundBase(unit,
                                            getMoveActionsToClosestOpponentUnits(unit, maxTargets),
                                            horizontalDistance, verticalDistance));
                        else if (radius > 0)
                            filteredActions.addAll(
                                    keepInsideCircularPerimeterAroundBase(unit,
                                            getMoveActionsToClosestOpponentUnits(unit, maxTargets),
                                            radius));
                        else // No perimeter, chase units close to self.
                            filteredActions.addAll(getMoveActionsToClosestOpponentUnits(unit, maxTargets));
                        break;
                }
            }
            else { // Explore all directions. Move aimlessly inside perimeter.
                if (horizontalDistance > 0 && verticalDistance > 0) // Rectangular Defense Perimeter
                    filteredActions.addAll(
                            keepInsideRectangularPerimeterAroundBase(unit, moveActions, horizontalDistance, verticalDistance));
                else if (radius > 0) // Circular Defense Perimeter
                    filteredActions.addAll(
                            keepInsideCircularPerimeterAroundBase(unit, moveActions, radius));
                else
                    filteredActions.addAll(moveActions);
            }
        }

        if (shuffleActions) Collections.shuffle(filteredActions);

        filteredActions.add(waitAction);
        return filteredActions;
    }

    /**
     * Checks whether the unit is outside the defense perimeter or not.
     *
     * @param horizontalDistance The horizontal distance from base in case of a rectangular defense perimeter.
     * @param verticalDistance The vertical distance from base in case of a rectangular defense perimeter.
     * @param radius The radius of the defense perimeter in case of a circular perimeter.
     * @return True, if the unit is outside the perimeter.
     */
    public boolean unitOutsideDefensePerimeter(int horizontalDistance, int verticalDistance, int radius ) {

        if (stateMonitor.getPlayerBases().isEmpty())
            return true;

        Unit base = stateMonitor.getPlayerBases().get(0);

        if (horizontalDistance > 0 && verticalDistance > 0)
            return (unit.getX() > base.getX() + horizontalDistance || unit.getX() < base.getX() - horizontalDistance ||
                    unit.getY() > base.getY() + verticalDistance || unit.getY() < base.getY() - verticalDistance);
        else if (radius > 0)
            return (euclideanDistanceNoSqrt(base.getX(), base.getY(), unit.getX(), unit.getY()) > radius * radius);

        return true;
    }

    /**
     * Restricts the movements of the given unit to a predefined vertical and horizontal distance away from own base.
     *
     * @param unit The unit in question.
     * @param moveActions The possible move actions.
     * @param horizontalDistance The horizontal distance from base.
     * @param verticalDistance The vertical distance from base.
     * @return A list of move actions, without the moves going beyond the perimeter around the base defined by the
     * vertical and horizontal distances.
     */
    private List<UnitAction> keepInsideRectangularPerimeterAroundBase(Unit unit, List<UnitAction> moveActions,
                                                                      int horizontalDistance, int verticalDistance) {

        if (stateMonitor.getPlayerBases().isEmpty())
            return moveActions;

        Unit base = stateMonitor.getPlayerBases().get(0);

        List<UnitAction> restrictedMoveActions = new LinkedList<>();

        for (UnitAction moveAction : moveActions) {
            switch (moveAction.getDirection()) {
                case UnitAction.DIRECTION_UP:
                    if (unit.getY() - 1 >= base.getY() - verticalDistance)
                        restrictedMoveActions.add(moveAction);
                    break;
                case UnitAction.DIRECTION_RIGHT:
                    if (unit.getX() + 1 <= base.getX() + horizontalDistance)
                        restrictedMoveActions.add(moveAction);
                    break;
                case UnitAction.DIRECTION_DOWN:
                    if (unit.getY() + 1 <= base.getY() + verticalDistance)
                        restrictedMoveActions.add(moveAction);
                    break;
                case UnitAction.DIRECTION_LEFT:
                    if (unit.getX() - 1 >= base.getX() - horizontalDistance)
                        restrictedMoveActions.add(moveAction);
                    break;
            }
        }
        return restrictedMoveActions;
    }

    /**
     * Similar to the previous method, but restricts movement to a circular area around base, defined by a given radius.
     *
     * @param unit The unit in question.
     * @param moveActions The possible move actions.
     * @param radius The radius of the perimeter.
     * @return A list of move unit actions, not going beyond the defense radius.
     */
    private List<UnitAction> keepInsideCircularPerimeterAroundBase(Unit unit, List<UnitAction> moveActions, double radius) {

        if (stateMonitor.getPlayerBases().isEmpty())
            return moveActions;

        Unit base = stateMonitor.getPlayerBases().get(0);

        List<UnitAction> restrictedMoveActions = new LinkedList<>();

        for (UnitAction moveAction : moveActions) {
            switch (moveAction.getDirection()) {
                case UnitAction.DIRECTION_UP:
                    if (euclideanDistanceNoSqrt(
                            base.getX(), base.getY(), unit.getX(), unit.getY() - 1) <= radius * radius)
                        restrictedMoveActions.add(moveAction);
                    break;
                case UnitAction.DIRECTION_RIGHT:
                    if (euclideanDistanceNoSqrt(
                            base.getX(), base.getY(), unit.getX() + 1, unit.getY()) <= radius * radius)
                        restrictedMoveActions.add(moveAction);
                    break;
                case UnitAction.DIRECTION_DOWN:
                    if (euclideanDistanceNoSqrt(
                            base.getX(), base.getY(), unit.getX(), unit.getY() + 1) <= radius * radius)
                        restrictedMoveActions.add(moveAction);
                    break;
                case UnitAction.DIRECTION_LEFT:
                    if (euclideanDistanceNoSqrt(
                            base.getX(), base.getY(), unit.getX() - 1, unit.getY()) <= radius * radius)
                        restrictedMoveActions.add(moveAction);
                    break;
            }
        }

        return restrictedMoveActions;
    }

    /**
     * Returns the euclidean distance, without applying the square root.
     * @param X1 X-coordinate of the 1st point.
     * @param Y1 Y-coordinate of the 1st point.
     * @param X2 X-coordinate of the 2nd point.
     * @param Y2 Y-coordinate of the 2nd point.
     * @return The distance between the two points.
     */
    private double euclideanDistanceNoSqrt(int X1, int Y1, int X2, int Y2) {
        return (X1 - X2) * (X1 - X2) + (Y1 - Y2) * (Y1 - Y2);
    }

    /**
     * Returns a list of UnitAction.TYPE_MOVE unit actions, each targeting an opponent unit. The opponent units are chosen
     * based on their proximity to the current unit.
     *
     * @param unit The unit in question.
     * @param maxTargets The number of close opponent units to consider.
     * @return UnitAction.TYPE_MOVE list.
     */
    private List<UnitAction> getMoveActionsToClosestOpponentUnits(Unit unit, int maxTargets) {
        return getMoveActionsToTargetsInRange(unit,
                stateMonitor.getOpponentUnitsClosestTo(unit, maxTargets), pathFinder);
    }

    /**
     * Returns a list of move unit-actions, targeting opponent units close to own base, if there is a base. If no base
     * exits, target units close to self.
     *
     * @param unit The unit in question.
     * @param maxTargets The maximum number of targets.
     * @return A list of move unit-actions.
     */
    private List<UnitAction> getMoveActionsToOpponentUnitsClosestToBase(Unit unit, int maxTargets) {
        if (!stateMonitor.getPlayerBases().isEmpty())
            return getMoveActionsToTargetsInRange(unit,
                    stateMonitor.getOpponentUnitsClosestTo(stateMonitor.getPlayerBases().get(0), maxTargets), pathFinder);
        else
            return getMoveActionsToTargetsInRange(unit,
                    stateMonitor.getOpponentUnitsClosestTo(unit, maxTargets), pathFinder);
    }

}

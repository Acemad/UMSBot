package preselection;

import ai.abstraction.pathfinding.PathFinding;
import rts.UnitAction;
import rts.units.Unit;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class OffenseManager extends HeuristicsManager {

    // Targeting Modes ************************************************************************************************
    public static final int TARGET_CLOSEST = 0; // Attack units closest to self.
    public static final int TARGET_CLOSEST_TO_BASE = 1; // Attack units closest to base.
    public static final int TARGET_MIN_HP = 2; // Attack units with smallest HP.
    public static final int TARGET_MAX_HP = 3; // Attack units with biggest HP.
    public static final int TARGET_RANDOM = 4; // Chose attack targets randomly.

    // Fixed Target ********************************************************************
    public static final int NO_FIXED_TARGET = 0; // No fixed target
    public static final int FIXED_TARGET_BASE_FIRST = 1; // Always target the opponent's base
    public static final int FIXED_TARGET_BARRACKS_FIRST = 2; // Always target the opponent's barracks
    public static final int FIXED_TARGET_ALL_STRUCTURES = 3; // Always target opponent structures.

    PathFinding pathFinder;

    public OffenseManager(StateMonitor stateMonitor, PathFinding pathFinder, Unit unit, List<UnitAction> unitActions) {
        super(stateMonitor, unit, unitActions);
        this.pathFinder = pathFinder;
    }

    /**
     * Filter unit-actions of an attack unit.
     * If attacking is possible, add all attack actions and a number (maxEscapes) of move actions, this is to restrict
     * excessive move actions, which may interfere with attacking. If attacking is not possible and the unit can move,
     * then move towards an opponent unit, depending on the chose attack mode.
     *
     * @param maxTargets The maximum number of units to target.
     * @param maxEscapes The maximum number of escapes allowed.
     * @return A filtered unit-actions list.
     */
    public List<UnitAction> filterActions(int maxTargets, int maxEscapes, int attackMode, float epsilonAttackMovement,
                                          int fixedTarget, boolean shuffleActions) {

        List<UnitAction> filteredActions = new LinkedList<>();

        if (!attackActions.isEmpty()) { // Attacking is possible.
            filteredActions.addAll(attackActions);
            filteredActions.addAll(choseRandomActionsFrom(moveActions, maxEscapes)); // Add all possible move actions (escapes)
        } else if (!moveActions.isEmpty()) {
            if (random.nextFloat() >= epsilonAttackMovement) { // Exploit. Chase a number of close opponent units.
                switch (attackMode) {
                    case TARGET_CLOSEST:
                        filteredActions.addAll(getMoveActionsToClosestOpponentUnits(maxTargets));
                        break;
                    case TARGET_CLOSEST_TO_BASE:
                        filteredActions.addAll(getMoveActionsToOpponentUnitsClosestToBase(maxTargets));
                        break;
                    case TARGET_MAX_HP:
                        filteredActions.addAll(getMoveActionsToHighestHPOpponentUnits(maxTargets));
                        break;
                    case TARGET_MIN_HP:
                        filteredActions.addAll(getMoveActionsToLowestHPOpponentUnits(maxTargets));
                        break;
                    case TARGET_RANDOM:
                        filteredActions.addAll(getMoveActionsToRandomOpponentUnits(maxTargets));
                }
            } else // Explore. Return all movement directions.
                filteredActions.addAll(moveActions);
//                filteredActions.addAll(getMoveActionsToRandomOpponentUnits(maxTargets));

            filteredActions.addAll(getMoveActionsToFixedOpponentTarget(fixedTarget));
        }

        if (shuffleActions) Collections.shuffle(filteredActions);

        filteredActions.add(waitAction);
        return filteredActions;
    }

    /**
     * Returns a list of UnitAction.TYPE_MOVE unit actions, each targeting an opponent unit. The opponent units are chosen
     * based on their proximity to the current unit.
     *
     * @param maxTargets The number of close opponent units to consider.
     * @return UnitAction.TYPE_MOVE list.
     */
    private List<UnitAction> getMoveActionsToClosestOpponentUnits(int maxTargets) {
        return getMoveActionsToTargetsInRange(unit,
                stateMonitor.getOpponentUnitsClosestTo(unit, maxTargets), pathFinder);
    }

    /**
     * Returns a list of move unit-actions, targeting opponent units close to own base, if there is a base. If no base
     * exits, target units close to self.
     *
     * @param maxTargets The maximum number of targets.
     * @return A list of move unit-actions.
     */
    private List<UnitAction> getMoveActionsToOpponentUnitsClosestToBase(int maxTargets) {
        if (!stateMonitor.getPlayerBases().isEmpty())
            return getMoveActionsToTargetsInRange(unit,
                    stateMonitor.getOpponentUnitsClosestTo(stateMonitor.getPlayerBases().get(0), maxTargets), pathFinder);
        else
            return getMoveActionsToTargetsInRange(unit,
                    stateMonitor.getOpponentUnitsClosestTo(unit, maxTargets), pathFinder);
    }

    /**
     * Returns a list of move unit-actions targeting enemy units having the lowest HP.
     *
     * @param maxTargets The maximum number of targets.
     * @return A list of move unit-actions.
     */
    private List<UnitAction> getMoveActionsToLowestHPOpponentUnits(int maxTargets) {
        return getMoveActionsToTargetsInRange(unit,
                stateMonitor.getOpponentUnitsLowestHP(maxTargets), pathFinder);
    }

    /**
     * Returns a list of move unit-actions targeting enemy units having the highest HP.
     *
     * @param maxTargets The maximum number of targets.
     * @return A list of move unit-actions.
     */
    private List<UnitAction> getMoveActionsToHighestHPOpponentUnits(int maxTargets) {
        return getMoveActionsToTargetsInRange(unit,
                stateMonitor.getOpponentUnitsHighestHP(maxTargets), pathFinder);
    }

    /**
     * Returns a list of move unit-actions targeting random enemy units.
     *
     * @param maxTargets The maximum number of targets.
     * @return A list of move unit-actions.
     */
    private List<UnitAction> getMoveActionsToRandomOpponentUnits(int maxTargets) {
        return getMoveActionsToTargetsInRange(unit,
                stateMonitor.getOpponentUnitsRandom(maxTargets), pathFinder);
    }


    /**
     * Returns move actions targeting fixed opponent unit(s). The target type is defined by the fixedTarget parameter.
     * This is useful for keeping track of high-profile targets that yield the highest reward when eliminated.
     *
     * @param fixedTarget The parameter defining which opponent unit-type to target.
     * @return A List of move UnitActions leading to the targeted unit(s).
     */
    private List<UnitAction> getMoveActionsToFixedOpponentTarget(int fixedTarget) {
        switch (fixedTarget) {
            case NO_FIXED_TARGET:
                return Collections.emptyList();
            case FIXED_TARGET_BASE_FIRST:
                if (!stateMonitor.getOpponentBases().isEmpty())
                    return getMoveActionsToTargetsInRange(unit,
                            stateMonitor.getOpponentBases().subList(0, 1), pathFinder);

                else if (!stateMonitor.getOpponentBarracks().isEmpty())
                    return getMoveActionsToTargetsInRange(unit,
                            stateMonitor.getOpponentBarracks().subList(0, 1), pathFinder);
                break;
            case FIXED_TARGET_BARRACKS_FIRST:
                if (!stateMonitor.getOpponentBarracks().isEmpty())
                    return getMoveActionsToTargetsInRange(unit,
                            stateMonitor.getOpponentBarracks().subList(0, 1), pathFinder);

                else if (!stateMonitor.getOpponentBases().isEmpty())
                    return getMoveActionsToTargetsInRange(unit,
                            stateMonitor.getOpponentBases().subList(0, 1), pathFinder);
                break;
            case FIXED_TARGET_ALL_STRUCTURES:
                List<UnitAction> fixedTargetMoves = new LinkedList<>();
                if (!stateMonitor.getOpponentBases().isEmpty())
                    fixedTargetMoves.addAll(getMoveActionsToTargetsInRange(unit,
                            stateMonitor.getOpponentBases().subList(0, 1), pathFinder));
                if (!stateMonitor.getOpponentBarracks().isEmpty())
                    fixedTargetMoves.addAll(getMoveActionsToTargetsInRange(unit,
                            stateMonitor.getOpponentBarracks().subList(0, 1), pathFinder));
                return fixedTargetMoves;
        }
        return Collections.emptyList();
    }
}

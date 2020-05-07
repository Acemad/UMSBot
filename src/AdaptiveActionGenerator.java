import ai.abstraction.pathfinding.*;
import rts.GameState;
import rts.PhysicalGameState;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;
import util.Pair;

import java.util.*;

/**
 * This class implements an adaptive move generation approach inspired by the CmabAsymmetricGenerator by @rubens
 * Not final, WIP.
 * @author Acemad
 */
public class AdaptiveActionGenerator {

    // Defense Modes
    public static final int DEFEND_BASE = 1, DEFEND_AROUND_SELF = 2; // TODO
    // Attack Modes
    public static final int ATTACK_CLOSEST = 1, ATTACK_CLOSEST_TO_BASE = 2, ATTACK_MIN_HP = 3, ATTACK_MAX_HP = 4,
            ATTACK_RANDOM = 5; // TODO
    // Harvest Modes
    public static final int AUTO_HARVEST = 1; // TODO

    // Building / Training Modes
    public static final int BUILD_OR_TRAIN_AT_RANDOM_LOCATION = 0, BUILD_AWAY_FROM_BASE = 1, TRAIN_OUTER_SIDE = 2; // TODO


    // The limits imposed on the number of possible units, of each type.
    // 0 : do not produce, -1 : produce infinitely, n : produce n at most.
    private static int maxBases = 1, maxBarracks = 1 ; //2
    private static int maxHarvesters = 2;
    private static int maxAttackWorkers = 4, maxAttackLights = 0, maxAttackRanged = -1,maxAttackHeavies = 0;
    private static int maxDefenseWorkers = 0, maxDefenseLights = 0, maxDefenseRanged = 1, maxDefenseHeavies = 0;

    // Front Line tactics, -1 maxFontLineUnits means take all units in the front-line
    private static int maxFrontLineUnits = 3, frontLineTacticalDistance = 1, frontLineWaitDuration = 3;
    private static int backWaitDuration = 10;

    // Defense
    private static int horizontalDistanceFromBase = 9, verticalDistanceFromBase = 9, radiusFromBase = 0,
            maxTargetsOnDefense = 2, defenseMode = DEFEND_BASE;

    // Attack
    private static int maxEscapes = 1, maxTargetsOnAttack = 2, attackMode = ATTACK_MAX_HP;

    // Production : Building / Training
    private static int maxProduceActionsChosen = 1, buildingMode = BUILD_OR_TRAIN_AT_RANDOM_LOCATION,
            trainingMode = BUILD_OR_TRAIN_AT_RANDOM_LOCATION;

    // Exploration parameter for movements. 1 : Random, 0 : Focused
    private static float epsilonDefenseMovement = 0.05f;
    private static float epsilonAttackMovement = 0.05f;
    private static float epsilonHarvestMovement = 0.05f;

    // ***************************************************************

    // For random number generation.
    private Random random = new Random();

    // The current count of each own unit type in this game state.
    private int nbBases = 0, nbBarracks = 0;
    private int nbHarvesters = 0;
    private int nbAttackWorkers = 0, nbAttackLights = 0, nbAttackRanged = 0, nbAttackHeavies = 0;
    private int nbDefenseWorkers = 0, nbDefenseLights = 0, nbDefenseRanged = 0, nbDefenseHeavies = 0;

    // The current game state and resource usage.
    private GameState gameState;
    private PhysicalGameState physicalGameState;
    private ResourceUsage resourceUsage;
    private int playerID; // ID of the searching player.
    private List<Pair<Unit, List<UnitAction>>> choices; // The list of action choices, for each unit.
    private long size = 1; // Size of the possible choice combination.

    // The path finding algorithm used to direct movement.
    private PathFinding pathFinder = new AStarPathFinding(); /*new FloodFillPathFinding();*/ /*new BFSPathFinding();*/ /*new GreedyPathFinding();*/

    // Units decomposition based on opponent proximity, used for adaptive wait durations.
    private List<Unit> frontLineUnits = new ArrayList<>();
    private List<Unit> backUnits = new ArrayList<>();

    // Decomposition of own units, based on UnitType
    private List<Unit> bases = new LinkedList<>();
    private List<Unit> barracks = new LinkedList<>();
    private List<Unit> workerUnits = new LinkedList<>();
    private List<Unit> lightUnits = new LinkedList<>();
    private List<Unit> rangedUnits = new LinkedList<>();
    private List<Unit> heavyUnits = new LinkedList<>();
    private List<Unit> harvestingWorkers = new LinkedList<>();

    // Decomposition of own mobile units, based on tactical stance.
    private List<Unit> attackingUnits = new LinkedList<>();
    private List<Unit> defendingUnits = new LinkedList<>();

    // All the opponent's units.
    private List<Unit> opponentUnits = new LinkedList<>();

    /**
     * The main constructor, populates the choices list with all the possible actions, relying on the different
     * decompositions and heuristics.
     *
     * @param gameState
     * @param playerID
     * @throws Exception
     */
    public AdaptiveActionGenerator(GameState gameState, int playerID) throws Exception {

        this.gameState = gameState;
        physicalGameState = gameState.getPhysicalGameState();
        resourceUsage = gameState.getResourceUsage();

        this.playerID = playerID;
        choices = new ArrayList<>();

        // Enumerates and decomposes all the units on the map.
        countUnits();

        // Select front line units and back units.
        selectFrontLineUnitsByOwnRange(maxFrontLineUnits, frontLineTacticalDistance);
//        selectFrontLineUnitsByOpponentRange(maxFrontLineUnits, frontLineTacticalDistance);
        selectBackUnits();

//        if (playerID == 1)
//            System.out.println("Front Lines : " + frontLineUnits.size());

        // Assign unit actions to each unit group.
        generateLowLevelActionsFor(frontLineUnits, frontLineWaitDuration, true);
        generateLowLevelActionsFor(backUnits, backWaitDuration, true);

        if (choices.size() == 0) {
            System.err.println("Problematic game state:");
            System.err.println(gameState);
            throw new Exception(
                    "Move generator for player " + playerID + " created with no units that can execute actions! (status: "
                            + gameState.canExecuteAnyAction(0) + ", " + gameState.canExecuteAnyAction(1) + ")"
            );
        }
    }


    /**
     * Enumerate and decompose units by UnitType and tactical stance.
     * TODO : remove count variables and replace with a call to size.
     */
    private void countUnits() {

        for (Unit unit : physicalGameState.getUnits()) {
            if (unit.getPlayer() == playerID) {
                switch (unit.getType().name) {

                    case "Base":
                        bases.add(unit);
                        nbBases++;
                        break;

                    case "Barracks":
                        barracks.add(unit);
                        nbBarracks++;
                        break;

                    case "Worker":
                        workerUnits.add(unit);

                        // Account for the Barracks being built.
                        UnitAction action = gameState.getUnitAction(unit);
                        if (action != null &&
                                action.getType() == UnitAction.TYPE_PRODUCE &&
                                action.getUnitType().name.equals("Barracks"))
                            nbBarracks++;

                        // Designate harvesting units, if there is a base.
                        if (nbBases > 0 && (maxHarvesters == -1 || nbHarvesters < maxHarvesters)) {
                            harvestingWorkers.add(unit);
                            nbHarvesters++;
                        }
                        else if (maxDefenseWorkers == -1 || nbDefenseWorkers < maxDefenseWorkers) {
                            defendingUnits.add(unit);
                            nbDefenseWorkers++;
                        }
                        else if (maxAttackWorkers == -1 || nbAttackWorkers < maxAttackWorkers) {
                            attackingUnits.add(unit);
                            nbAttackWorkers++;
                        }

                        break;

                    case "Light":
                        lightUnits.add(unit);

                        if (maxDefenseLights == -1 || nbDefenseLights < maxDefenseLights) {
                            defendingUnits.add(unit);
                            nbDefenseLights++;
                        }
                        else if (maxAttackLights == -1 || nbAttackLights < maxAttackLights) {
                            attackingUnits.add(unit);
                            nbAttackLights++;
                        }

                        break;

                    case "Ranged":
                        rangedUnits.add(unit);

                        if (maxDefenseRanged == -1 || nbDefenseRanged < maxDefenseRanged) {
                            defendingUnits.add(unit);
                            nbDefenseRanged++;
                        }
                        else if (maxAttackRanged == -1 || nbAttackRanged < maxAttackRanged) {
                            attackingUnits.add(unit);
                            nbAttackRanged++;
                        }

                        break;

                    case "Heavy":
                        heavyUnits.add(unit);

                        if (maxDefenseHeavies == -1 || nbDefenseHeavies < maxDefenseHeavies) {
                            defendingUnits.add(unit);
                            nbDefenseHeavies++;
                        }
                        else if (maxAttackHeavies == -1 || nbAttackHeavies < maxAttackHeavies) {
                            attackingUnits.add(unit);
                            nbAttackHeavies++;
                        }

                        break;

                    default:
                        break;
                }
            } else // Enumerate opponent units.
                if (unit.getPlayer() == 1 - playerID)
                    opponentUnits.add(unit);
        }
    }

    /**
     * Selects own units that have an opponent unit within their attack range.
     * @param tacticalDistance
     * @param unitLimit
     */
    private void selectFrontLineUnitsByOwnRange(int unitLimit, int tacticalDistance) {

        for (Unit unit : physicalGameState.getUnits()) {

            if (unit.getPlayer() == playerID && unit.getType().canMove) {

                for (Unit otherUnit : physicalGameState.getUnits()) {
                    if (otherUnit.getPlayer() == 1 - playerID && otherUnit.getType().canMove &&
                        (Math.abs(otherUnit.getX() - unit.getX()) <= unit.getAttackRange() + tacticalDistance &&
                         Math.abs(otherUnit.getY() - unit.getY()) <= unit.getAttackRange() + tacticalDistance)) {

                        if (unitLimit < 0 || frontLineUnits.size() < unitLimit)
                            frontLineUnits.add(unit);
                        break;
                    }
                }
            }

            if (unitLimit >= 0 && frontLineUnits.size() >= unitLimit)
                break;
        }
    }

    /**
     * Selects own units that are in the attack range of an opponent unit.
     * @param tacticalDistance
     * @param unitLimit
     */
    private void selectFrontLineUnitsByOpponentRange(int unitLimit, int tacticalDistance) {

        for (Unit unit : physicalGameState.getUnits()) {

            if (unit.getPlayer() == 1 - playerID && unit.getType().canMove) {

                for (Unit otherUnit : physicalGameState.getUnits()) {
                    if (!frontLineUnits.contains(otherUnit) &&
                        otherUnit.getPlayer() == playerID && otherUnit.getType().canMove &&
                        (Math.abs(otherUnit.getX() - unit.getX()) <= unit.getAttackRange() + tacticalDistance &&
                         Math.abs(otherUnit.getY() - unit.getY()) <= unit.getAttackRange() + tacticalDistance))

                        if (unitLimit < 0 || frontLineUnits.size() < unitLimit)
                            frontLineUnits.add(otherUnit);
                        else
                            break;
                }
            }

            if (unitLimit >= 0 && frontLineUnits.size() >= unitLimit)
                break;
        }
    }

    /**
     * The remaining unselected units, that are not in the front line units list, are all considered back units.
     */
    private void selectBackUnits() {

        backUnits.clear();

        for (Unit unit : physicalGameState.getUnits()) {
            if (unit.getPlayer() == playerID && !frontLineUnits.contains(unit))
                backUnits.add(unit);
        }
    }

    /**
     * Generate low-level unit actions for a given group of units. The wait action generated is assigned a predefined
     * duration. Unit-actions are filtered heuristically before returning.
     *
     * @param unitGroup The considered group of units.
     * @param waitDuration The duration of the wait action.
     * @param heuristicFiltering If true, heuristic filtering is applied to each unit and its unit actions.
     */
    private void generateLowLevelActionsFor(List<Unit> unitGroup, int waitDuration, boolean heuristicFiltering) {
        for (Unit unit : unitGroup) {
            if (gameState.getUnitActions().get(unit) == null) {
                List<UnitAction> unitActions = unit.getUnitActions(gameState, waitDuration);
                if (heuristicFiltering)
                    unitActions = heuristicFiltering(unit, unitActions);
                choices.add(new Pair<>(unit, unitActions));

                long tmp = unitActions.size();
                if (Long.MAX_VALUE / size <= tmp)
                    size = Long.MAX_VALUE;
                else
                    size *= unitActions.size();
            }
        }
    }

    /**
     * This method performs what we call heuristic filtering or pruning on the set of possible unit-actions of a unit.
     * Depending on the unit type it will discard a number of actions judged less useful depending on the context.
     * It also reduces the number of produce actions to a single direction, instead of four.
     *
     * @param unit The unit in question.
     * @param unitActions All the possible unit-actions.
     * @return A filtered list of unit-actions.
     */
    private List<UnitAction> heuristicFiltering(Unit unit, List<UnitAction> unitActions) {

        // The filtered list of unit-actions to return
        List<UnitAction> filteredActions = new LinkedList<>();

        // unitActions decomposition into each respective action type.
        List<UnitAction> moveActions = new LinkedList<>(); // TYPE_MOVE
        List<UnitAction> harvestActions = new LinkedList<>(); // TYPE_HARVEST and TYPE_RETURN
        List<UnitAction> attackActions = new LinkedList<>(); // TYPE_ATTACK_LOCATION

        // TYPE_PRODUCE, each correspond to the unit type to produce.
        List<UnitAction> produceBarracksActions = new LinkedList<>();
        List<UnitAction> produceBaseActions = new LinkedList<>();
        List<UnitAction> produceWorkerActions = new LinkedList<>();
        List<UnitAction> produceLightActions = new LinkedList<>();
        List<UnitAction> produceRangedActions = new LinkedList<>();
        List<UnitAction> produceHeavyActions = new LinkedList<>();

        // TYPE_NONE
        UnitAction waitAction = null;

        // True if a produce action is possible.
        boolean canProduce = false;

        // The decomposition of unitActions list.
        for (UnitAction unitAction : unitActions) {
            switch (unitAction.getType()) {
                case UnitAction.TYPE_HARVEST:
                case UnitAction.TYPE_RETURN:
                    harvestActions.add(unitAction);
                    break;
                case UnitAction.TYPE_ATTACK_LOCATION:
                    attackActions.add(unitAction);
                    break;
                case UnitAction.TYPE_PRODUCE:
                    canProduce = true;
                    switch (unitAction.getUnitType().name) {
                        case "Barracks":
                            produceBarracksActions.add(unitAction); break;
                        case "Base":
                            produceBaseActions.add(unitAction); break;
                        case "Worker":
                            produceWorkerActions.add(unitAction); break;
                        case "Light":
                            produceLightActions.add(unitAction); break;
                        case "Ranged":
                            produceRangedActions.add(unitAction); break;
                        case "Heavy":
                            produceHeavyActions.add(unitAction); break;
                        default:
                            break;
                    }
                    break;
                case UnitAction.TYPE_MOVE:
                    moveActions.add(unitAction);
                    break;
                case UnitAction.TYPE_NONE:
                    waitAction = unitAction;
                    break;
            }
        }

        // The unit is a harvesting worker.
        if (harvestingWorkers.contains(unit)) {

           filteredActions.addAll(
                   filterHarvesterActions(unit, harvestActions, moveActions, produceBarracksActions, produceBaseActions,
                           maxProduceActionsChosen));

           filteredActions.add(waitAction);
           return filteredActions;
        }

        // The unit is adopting a defensive stance.
        if (defendingUnits.contains(unit)) {

            filteredActions.addAll(filterDefenseActions(unit, moveActions, attackActions,
                    horizontalDistanceFromBase, verticalDistanceFromBase, radiusFromBase, maxTargetsOnDefense));
            filteredActions.add(waitAction);
            return filteredActions;
        }

        // The unit is adopting an attacking stance.
        if (attackingUnits.contains(unit)) {

            filteredActions.addAll(filterAttackActions(unit, moveActions, attackActions, maxTargetsOnAttack, maxEscapes));

            filteredActions.add(waitAction);
            return filteredActions;
        }

        // The unit is a barracks.
        if (barracks.contains(unit)) {
            // Production is possible, either train a Light, Ranged, or Heavy unit, within the limits.
            if (canProduce) {
                filteredActions.addAll(filterBarracksActions(produceLightActions, produceRangedActions,
                        produceHeavyActions, maxProduceActionsChosen));
            }

            filteredActions.add(waitAction);
            return filteredActions;
        }

        // The unit is a base.
        if (bases.contains(unit)) {
            // Production is possible, train workers within the limits.
            if (canProduce) {
                filteredActions.addAll(filterBaseActions(produceWorkerActions, maxProduceActionsChosen));
            }

            filteredActions.add(waitAction);
            return filteredActions;
        }

        return unitActions;
    }

    private List<UnitAction> filterHarvesterActions(Unit unit, List<UnitAction> harvestActions,List<UnitAction> moveActions,
                                                    List<UnitAction> produceBarracksActions,
                                                    List<UnitAction> produceBaseActions, int maxProduceActionsChosen) {

        List<UnitAction> filteredActions = new LinkedList<>();

        // Harvesting is possible (Return/Harvest)
        if (!harvestActions.isEmpty())
            filteredActions.addAll(harvestActions);
        else {
            // In between the harvesting actions, check if building is possible under the limits.
            if (!produceBarracksActions.isEmpty() && (maxBarracks == -1 || nbBarracks < maxBarracks))
                switch (buildingMode) {
                    case BUILD_OR_TRAIN_AT_RANDOM_LOCATION:
                        filteredActions.addAll(choseRandomActionsFrom(produceBarracksActions, maxProduceActionsChosen));
                    case BUILD_AWAY_FROM_BASE:
                        filteredActions.addAll(
                                choseBuildingPositionsNonAdjacentToBase(unit, produceBarracksActions, maxProduceActionsChosen));
                }
            else if (!produceBaseActions.isEmpty() && (maxBases == -1 || nbBases < maxBases))
                filteredActions.addAll(choseRandomActionsFrom(produceBarracksActions, maxProduceActionsChosen));

            // Move towards the closest base or the closest resource deposit
            if (!moveActions.isEmpty())
                if (random.nextFloat() >= epsilonHarvestMovement)
                    filteredActions.add(getHarvestActions(unit, moveActions));
                else
                    filteredActions.addAll(moveActions);
        }
        return filteredActions;
    }

    private List<UnitAction> filterDefenseActions(Unit unit, List<UnitAction> moveActions, List<UnitAction> attackActions,
                                                  int horizontalDistance, int verticalDistance, int radius, int maxTargets) {

        List<UnitAction> filteredActions = new LinkedList<>();

        filteredActions.addAll(attackActions); // Attack actions are always added.

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
                        else
                            filteredActions.addAll(getMoveActionsToOpponentUnitsClosestToBase(unit, maxTargets));
                        break;

                    case DEFEND_AROUND_SELF: // Chase the opponent units closest to self.
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
                        else
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

        return filteredActions;
    }

    private List<UnitAction> filterAttackActions(Unit unit, List<UnitAction> moveActions,
                                                 List<UnitAction> attackActions, int maxTargets, int maxEscapes) {

        List<UnitAction> filteredActions = new LinkedList<>();

        if (!attackActions.isEmpty()) { // Attacking is possible.
            filteredActions.addAll(attackActions);
            filteredActions.addAll(choseRandomActionsFrom(moveActions, maxEscapes)); // Add all possible move actions (escapes) // TODO : restrict escape moves
        } else if (!moveActions.isEmpty()) {
            if (random.nextFloat() >= epsilonAttackMovement) { // Exploit. Chase a number of close opponent units.
                switch (attackMode) {
                    case ATTACK_CLOSEST:
                        filteredActions.addAll(getMoveActionsToClosestOpponentUnits(unit, maxTargets));
                        break;
                    case ATTACK_CLOSEST_TO_BASE:
                        filteredActions.addAll(getMoveActionsToOpponentUnitsClosestToBase(unit, maxTargets));
                        break;
                    case ATTACK_MAX_HP:
                        filteredActions.addAll(getMoveActionsToHighestHPOpponentUnits(unit, maxTargets));
                        break;
                    case ATTACK_MIN_HP:
                        filteredActions.addAll(getMoveActionsToLowestHPOpponentUnits(unit, maxTargets));
                        break;
                    case ATTACK_RANDOM:
                        filteredActions.addAll(getMoveActionsToRandomOpponentUnits(unit, maxTargets));
                }
            } else // Explore. Return all movement directions.
                filteredActions.addAll(moveActions);
        }

        return filteredActions;
    }

    private List<UnitAction> filterBaseActions(List<UnitAction> produceWorkerActions, int maxProduceActionsChosen) {

        List<UnitAction> filteredActions = new LinkedList<>();

        if (!produceWorkerActions.isEmpty() && // Production/Training is possible. Check limits and perform.
                ((maxHarvesters == -1 || nbHarvesters < maxHarvesters) ||
                (maxDefenseWorkers == -1 || nbDefenseWorkers < maxDefenseWorkers) ||
                (maxAttackWorkers == -1 || nbAttackWorkers < maxAttackWorkers)))

            filteredActions.addAll(choseRandomActionsFrom(produceWorkerActions, maxProduceActionsChosen));

        return filteredActions;
    }

    private List<UnitAction> filterBarracksActions(List<UnitAction> produceLightActions, List<UnitAction> produceRangedActions,
                                                   List<UnitAction> produceHeavyActions, int maxProduceActionsChosen) {

        List<UnitAction> filteredActions = new LinkedList<>();

        // Priority to defense units.
        if (!produceLightActions.isEmpty() &&
                (maxDefenseLights == -1 || nbDefenseLights < maxDefenseLights))
            filteredActions.addAll(choseRandomActionsFrom(produceLightActions, maxProduceActionsChosen));
        else if (!produceRangedActions.isEmpty() &&
                (maxDefenseRanged == -1 || nbDefenseRanged < maxDefenseRanged))
            filteredActions.addAll(choseRandomActionsFrom(produceRangedActions, maxProduceActionsChosen));
        else if (!produceHeavyActions.isEmpty() &&
                (maxDefenseHeavies == -1 || nbDefenseHeavies < maxDefenseHeavies))
            filteredActions.addAll(choseRandomActionsFrom(produceHeavyActions, maxProduceActionsChosen));

        else {
            // Train any unit.
            if (!produceLightActions.isEmpty() &&
                    (maxAttackLights == -1 || nbAttackLights < maxAttackLights))
                filteredActions.addAll(choseRandomActionsFrom(produceLightActions, maxProduceActionsChosen));

            if (!produceRangedActions.isEmpty() &&
                    (maxAttackRanged == -1 || nbAttackRanged < maxAttackRanged))
                filteredActions.addAll(choseRandomActionsFrom(produceRangedActions, maxProduceActionsChosen));

            if (!produceHeavyActions.isEmpty() &&
                    (maxAttackHeavies == -1 || nbAttackHeavies < maxAttackHeavies))
                filteredActions.addAll(choseRandomActionsFrom(produceHeavyActions, maxProduceActionsChosen));
        }

        return filteredActions;
    }

    /**
     * Returns a list of opponent units around a given unit, in the immediate square range.
     * @param unit The unit considered.
     * @param squareRange The maximum distance searched around the unit.
     * @return
     */
    private List<Unit> getOpponentUnitsAround(Unit unit, int squareRange) {
        List<Unit> closeUnits = new LinkedList<>();

        for (Unit possibleUnit : physicalGameState.getUnits()) {
            if (possibleUnit.getPlayer() == 1 - playerID &&
                (Math.abs(possibleUnit.getX() - unit.getX()) <= squareRange &&
                 Math.abs(possibleUnit.getY() - unit.getY()) <= squareRange)) {
                closeUnits.add(possibleUnit);
            }
        }
        return closeUnits;
    }

    /**
     * Returns a list of UnitAction.TYPE_MOVE unit actions, each targeting an opponent unit. The opponent units are chosen
     * based on their proximity to the current unit.
     *
     * @param unit The units in question.
     * @param maxTargets The number of close opponent units to consider.
     * @return UnitAction.TYPE_MOVE list.
     */
    private List<UnitAction> getMoveActionsToClosestOpponentUnits(Unit unit, int maxTargets) {
        return getDirectedMoveActions(unit, getClosestOpponentUnitsTo(unit, maxTargets));
    }

    private List<UnitAction> getMoveActionsToOpponentUnitsClosestToBase(Unit unit, int maxTargets) {
        if (!bases.isEmpty())
            return getDirectedMoveActions(unit, getClosestOpponentUnitsTo(bases.get(0), maxTargets));
        else
            return getDirectedMoveActions(unit, getClosestOpponentUnitsTo(unit, maxTargets));
    }

    private List<UnitAction> getMoveActionsToLowestHPOpponentUnits(Unit unit, int maxTargets) {
        return getDirectedMoveActions(unit, getLowestHPOpponentUnits(maxTargets));
    }

    private List<UnitAction> getMoveActionsToHighestHPOpponentUnits(Unit unit, int maxTargets) {
        return getDirectedMoveActions(unit, getHighestHPOpponentUnits(maxTargets));
    }

    private List<UnitAction> getMoveActionsToRandomOpponentUnits(Unit unit, int maxTargets) {
        return getDirectedMoveActions(unit, getRandomOpponentUnits(maxTargets));
    }

    private List<UnitAction> getDirectedMoveActions(Unit unit, List<Unit> targetUnits) {
        List<UnitAction> directedMoveActions = new LinkedList<>();
        for (Unit opponentUnit : targetUnits) {
            int targetPosition = opponentUnit.getX() + opponentUnit.getY() * physicalGameState.getWidth();
            UnitAction directedMove = pathFinder.findPathToPositionInRange(
                    unit, targetPosition, unit.getAttackRange(), gameState, resourceUsage);
            if (directedMove != null)
                directedMoveActions.add(directedMove);
        }
        return directedMoveActions;
    }



    private List<Unit> getLowestHPOpponentUnits(int maxTargets) {
        List<Unit> opponentUnits = new LinkedList<>(this.opponentUnits);
        List<Unit> lowestHPOpponentUnits = new LinkedList<>();

        if (opponentUnits.size() > maxTargets) {
            while (lowestHPOpponentUnits.size() < maxTargets) {
                int lowestHP = 0;
                Unit lowestHPUnit = null;
                for (Unit opponentUnit : opponentUnits) {
                    int unitHP = opponentUnit.getHitPoints();
                    if (lowestHPUnit == null || unitHP < lowestHP) {
                        lowestHP = unitHP;
                        lowestHPUnit = opponentUnit;
                    }
                }
                lowestHPOpponentUnits.add(lowestHPUnit);
                opponentUnits.remove(lowestHPUnit);
            }

        } else
            return opponentUnits;

        return lowestHPOpponentUnits;
    }

    private List<Unit> getHighestHPOpponentUnits(int maxTargets) {
        List<Unit> opponentUnits = new LinkedList<>(this.opponentUnits);
        List<Unit> highestHPOpponentUnits = new LinkedList<>();

        if (opponentUnits.size() > maxTargets) {
            while (highestHPOpponentUnits.size() < maxTargets) {
                int highestHP = 0;
                Unit highestHPUnit = null;
                for (Unit opponentUnit : opponentUnits) {
                    int unitHP = opponentUnit.getHitPoints();
                    if (highestHPUnit == null || unitHP > highestHP) {
                        highestHP = unitHP;
                        highestHPUnit = opponentUnit;
                    }
                }
                highestHPOpponentUnits.add(highestHPUnit);
                opponentUnits.remove(highestHPUnit);
            }

        } else
            return opponentUnits;

        return highestHPOpponentUnits;
    }

    private List<Unit> getRandomOpponentUnits(int maxTargets) {
        List<Unit> opponentUnits = new LinkedList<>(this.opponentUnits);
        List<Unit> randomOpponentUnits = new LinkedList<>();

        if (opponentUnits.size() > maxTargets) {
            while (randomOpponentUnits.size() < maxTargets) {
                randomOpponentUnits.add(
                        opponentUnits.remove(random.nextInt(opponentUnits.size())));
            }
        } else
            return opponentUnits;

        return randomOpponentUnits;
    }

    /**
     * Finds and returns the closest n opponent units to the given unit.
     * @param unit The unit in question.
     * @param maxClosestOpponentUnits The number of close opponent units to consider.
     * @return a Unit list.
     */
    private List<Unit> getClosestOpponentUnitsTo(Unit unit, int maxClosestOpponentUnits) {

        List<Unit> opponentUnits = new LinkedList<>(this.opponentUnits);
        List<Unit> closestOpponentUnits = new LinkedList<>();

        int counter = 0;

        if (opponentUnits.size() > maxClosestOpponentUnits) {
            while (counter < maxClosestOpponentUnits) {
                int closestDistance = 0;
                Unit closestEnemy = null;
                for (Unit opponentUnit : opponentUnits) {
                    int distance = Math.abs(unit.getX() - opponentUnit.getX()) + Math.abs(unit.getY() - opponentUnit.getY());
                    if (closestEnemy == null || distance < closestDistance) {
                        closestDistance = distance;
                        closestEnemy = opponentUnit;
                    }
                }
                closestOpponentUnits.add(closestEnemy);
                opponentUnits.remove(closestEnemy);
                counter++;
            }
        } else
            return opponentUnits;

        return closestOpponentUnits;
    }

    /**
     * Returns the next action to consider for a harvesting unit. Either move towards the closest resource deposit,
     * or move towards the closest base.
     * TODO : join attack units if harvesting is not possible.
     * @param unit The worker unit in question.
     * @param possibleMoveActions The fallback move actions, in case path finding fails.
     * @return A TYPE_MOVE unit action.
     */
    private UnitAction getHarvestActions(Unit unit, List<UnitAction> possibleMoveActions) {

        if (unit.getResources() > 0) {
            Unit base = getClosestBaseTo(unit);
            if (base != null)
                return getMoveActionToTargetAdjacent(unit, base, possibleMoveActions);
        } else {
            Unit resource = getClosestResourceTo(unit);
            if (resource != null)
                return getMoveActionToTargetAdjacent(unit, resource, possibleMoveActions);
        }

        return possibleMoveActions.remove(random.nextInt(possibleMoveActions.size()));
    }

    /**
     * Returns the closest friendly base to a given unit.
     *
     * @param unit The unit in question.
     * @return The closest base.
     */
    private Unit getClosestBaseTo(Unit unit) {
        Unit closestBase = null;
        int closestDistance = 0;
        for (Unit possibleBase : physicalGameState.getUnits()) {
            if (possibleBase.getPlayer() == playerID && possibleBase.getType().isStockpile) {
                int distance = Math.abs(possibleBase.getX() - unit.getX()) + Math.abs(possibleBase.getY() - unit.getX());
                if (closestBase == null || distance < closestDistance) {
                    closestDistance = distance;
                    closestBase = possibleBase;
                }
            }
        }
        return closestBase;
    }

    /**
     * Returns the closest resource deposit to a given unit.
     *
     * @param unit The unit in question.
     * @return The closest resource deposit.
     */
    private Unit getClosestResourceTo(Unit unit) {
        Unit closestResource = null;
        int closestDistance = 0;
        for (Unit possibleResource : physicalGameState.getUnits()) {
            if (possibleResource.getType().isResource) {
                int distance = Math.abs(possibleResource.getX() - unit.getX()) + Math.abs(possibleResource.getY() - unit.getX());
                if (closestResource == null || distance < closestDistance) {
                    closestDistance = distance;
                    closestResource = possibleResource;
                }
            }
        }
        return closestResource;
    }

    /**
     * Returns a UnitAction.TYPE_MOVE action leading to an adjacent position to a target unit, meant for use by workers
     * to harvest and return resources. A fallback list of possible moves is given in case path finding fails to find
     * a move action.
     *
     * @param unit The unit in question.
     * @param target The target in question.
     * @param possibleMoveActions Fallback move actions.
     * @return A unit action of type : TYPE_MOVE
     */
    private UnitAction getMoveActionToTargetAdjacent(Unit unit, Unit target, List<UnitAction> possibleMoveActions) {
        int targetPosition = target.getX() + target.getY() * physicalGameState.getWidth();
        UnitAction moveTowardsTarget = pathFinder.findPathToAdjacentPosition(
                unit, targetPosition, gameState, resourceUsage);
        if (moveTowardsTarget != null)
            return moveTowardsTarget;
        else
            return possibleMoveActions.remove(random.nextInt(possibleMoveActions.size()));
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

        if (bases.isEmpty())
            return moveActions;

        Unit base = bases.get(0);

        // Unit already out. // TODO : remove unit from defense group and add to attack group.
        if (unit.getX() > base.getX() + horizontalDistance || unit.getX() < base.getX() - horizontalDistance ||
            unit.getY() > base.getY() + verticalDistance || unit.getY() < base.getY() - verticalDistance)
            return moveActions;

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

        if (bases.isEmpty())
            return moveActions;

        Unit base = bases.get(0);

        //Unit Already out. // TODO : remove unit from defense group and add to attack group.
        if (euclideanDistanceNoSqrt(base.getX(), base.getY(), unit.getX(), unit.getY()) > radius * radius)
            return moveActions;

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

    private double euclideanDistanceNoSqrt(int X1, int Y1, int X2, int Y2) {
        return (X1 - X2) * (X1 - X2) + (Y1 - Y2) * (Y1 - Y2);
    }

    /**
     * Chose a building position as far as possible from base.
     * @param unit
     * @param unitActions
     * @param maxChoices
     * @return
     */
    private List<UnitAction> choseBuildingPositionsNonAdjacentToBase(Unit unit, List<UnitAction> unitActions, int maxChoices) {

        if (bases.isEmpty())
            return choseRandomActionsFrom(unitActions, maxChoices);

        if (maxChoices == -1 || maxChoices >= unitActions.size())
            return unitActions;

        List<UnitAction> actions = new LinkedList<>(unitActions);
        List<UnitAction> chosenActions = new LinkedList<>();
        Unit base = bases.get(0);
        int positionX = unit.getX(), positionY = unit.getY();

        for (int counter = 0; counter < maxChoices; counter++) {
            UnitAction bestAction = null;
            int maxDistanceToBase = 0;

            for (UnitAction action : actions) {
                switch (action.getDirection()) {
                    case UnitAction.DIRECTION_UP:
                        positionY--;
                        break;
                    case UnitAction.DIRECTION_RIGHT:
                        positionX++;
                        break;
                    case UnitAction.DIRECTION_DOWN:
                        positionY++;
                        break;
                    case UnitAction.DIRECTION_LEFT:
                        positionX--;
                        break;
                }
                int distance = Math.abs(positionX - base.getX()) + Math.abs(positionY - base.getY());
                if (maxDistanceToBase == 0 || distance > maxDistanceToBase) {
                    maxDistanceToBase = distance;
                    bestAction = action;
                }
            }

            chosenActions.add(bestAction);
            actions.remove(bestAction);
        }

        return chosenActions;
    }

    /**
     * Selects a number of random actions from a list of given unit actions.
     * @param unitActions
     * @param maxChoices
     * @return
     */
    private List<UnitAction> choseRandomActionsFrom(List<UnitAction> unitActions, int maxChoices) {

        List<UnitAction> actions = new LinkedList<>(unitActions);
        List<UnitAction> chosenActions = new LinkedList<>();

        if (maxChoices == -1 || maxChoices >= unitActions.size())
            return unitActions;
        else {
            for (int counter = 0; counter < maxChoices; counter++)
                chosenActions.add(actions.remove(random.nextInt(actions.size())));

            return chosenActions;
        }
    }

    

    public List<Pair<Unit, List<UnitAction>>> getChoices() {
        return choices;
    }

    public List<Unit> getBackUnits() {
        return backUnits;
    }

    public List<Unit> getFrontLineUnits() {
        return frontLineUnits;
    }

    public long getSize() {
        return size;
    }
}

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
    public static final int DEFEND_BASE = 0; // Attack units closest to base.
    public static final int DEFEND_AROUND_SELF = 1; // Attack units closest to self.

    // Attack Modes
    public static final int ATTACK_CLOSEST = 0; // Attack units closest to self.
    public static final int ATTACK_CLOSEST_TO_BASE = 1; // Attack units closest to self.
    public static final int ATTACK_MIN_HP = 2; // Attack units with smallest HP.
    public static final int ATTACK_MAX_HP = 3; // Attack units with biggest HP.
    public static final int ATTACK_RANDOM = 4; // Chose attack targets randomly.

    // Building / Training Modes
    public static final int BUILD_OR_TRAIN_AT_RANDOM_LOCATION = 0; // Build or Train at a random location (adjacent to base/barracks)
    public static final int BUILD_AWAY_FROM_BASE = 1; // Build barracks away from base (not adjacent)
    public static final int TRAIN_OUTER_SIDE = 2; // Train units in the outer side of the structures.

    // Front-Line Selection Modes
    public static final int SELECT_FL_BY_OWN_RANGE = 0; // Select units having an opponent unit in their attack range.
    public static final int SELECT_FL_BY_OPPONENT_RANGE = 1; // Select units in the attack range of opponent units.

    // The limits imposed on the number of possible units, of each type.
    // 0 : do not produce, -1 : produce infinitely, n : produce n at most.
    private static int maxBases = 1; // The number of Bases to build, at most.
    private static int maxBarracks = 2 ; // The number of Barracks to build, at most.
    private static int maxHarvesters = 3; // The number of harvesting Workers to train, at most.
    private static int maxAttackWorkers = 2; // The number of attacking Workers to train, at most.
    private static int maxAttackLights = 0; // The number of attacking Lights to train, at most.
    private static int maxAttackRanged = 1; // The number of attacking Ranged to train, at most.
    private static int maxAttackHeavies = 0; // The number of attacking Heavies to train, at most.
    private static int maxDefenseWorkers = 0; // The number of defending Workers to train, at most.
    private static int maxDefenseLights = 2; // The number of defending Lights to train, at most.
    private static int maxDefenseRanged = -1; // The number of defending Ranged to train, at most.
    private static int maxDefenseHeavies = 2; // The number of defending Heavies to train, at most.

    // Front Line tactics, -1 maxFontLineUnits means take all units in the front-line
    private static int frontLineSelectionMode = SELECT_FL_BY_OWN_RANGE; // Front-Line selection method.
    private static int maxFrontLineUnits = 3; // Maximum number of front-line units to consider. -1: take all, 0: take none.
    private static int frontLineTacticalDistance = 1; // The tactical distance ahead of attack range.
    private static int frontLineWaitDuration = 3; // Wait duration of front-line units.
    private static int backWaitDuration = 10; // Wait duration of back units.

    // Defense : parameters defining the defense behavior.
    // Rectangular defense perimeter: If both vertical and horizontal distances are > 0, a rectangular defense perimeter
    // is set, otherwise, it is disabled and a circular defense perimeter may take its place.
    private static int horizontalDistanceFromBase = 0; // Rectangular defense: the horizontal distance from base.
    private static int verticalDistanceFromBase = 0; // Rectangular defense: the vertical distance from base.
    // Circular defense perimeter: if the radius is > 0, a circular defense perimeter is set, otherwise, it is disabled.
    // If both perimeter types dimensions are set to 0, defense is disabled.
    private static int radiusFromBase = 7; // The defense radius from base.
    private static int maxTargetsOnDefense = 2; // The maximum number of enemy units to target on defense mode.
    private static int defenseMode = DEFEND_BASE; // The defense mode in place.
    // The maximum number of cycles defense units will stay inside the perimeter. -1: stay indefinitely.
    private static int defenseTime = 500; // After each defenseTime period, a stance switch is triggered, from def to attack
    private static int switchDelay = 200; // The amount of time in which defense units are allowed to switch stance, after the trigger.

    // Attack : parameters defining the attack behavior.
    private static int maxEscapes = 1; // If a unit can attack and also move, maxEscapes limit the number of move actions allowed
    private static int maxTargetsOnAttack = 2; // The maximum number of enemy units to target on attack mode.
    private static int attackMode = ATTACK_CLOSEST; // The attack mode in uses.

    // Production : Building / Training
    private static int maxProduceActionsChosen = 2; // If a unit can produce, limit the number of produce actions to chose from.
    private static int buildingLocation = BUILD_AWAY_FROM_BASE; // The Barracks building location.
    private static int trainingLocation = TRAIN_OUTER_SIDE; // The mobile units training location.

    // Exploration parameter for movements. 1 : Random, 0 : Focused (using path finding)
    private static float epsilonHarvestMovement = 0.05f; // The exploration parameter of harvesting units.
    private static float epsilonDefenseMovement = 0.05f;                              // defense units.
    private static float epsilonAttackMovement = 0.05f;                               // attack units.

    // ***************************************************************

    // Shuffles the returned actions.
    private static boolean shuffleActions = true;

    // For random number generation.
    private Random random = new Random();

    // The current count of each own unit type in this game state.
    private int nbBases = 0;
    private int nbBarracks = 0;
    private int nbHarvesters = 0;
    private int nbAttackWorkers = 0;
    private int nbAttackLights = 0;
    private int nbAttackRanged = 0;
    private int nbAttackHeavies = 0;
    private int nbDefenseWorkers = 0;
    private int nbDefenseLights = 0;
    private int nbDefenseRanged = 0;
    private int nbDefenseHeavies = 0;

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
        manageDefenseTime();

        // Select front line units and back units.
        selectFrontLineUnits();
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
                UnitAction action;
                switch (unit.getType().name) {

                    case "Base":
                        bases.add(unit);
                        nbBases++;
                        break;

                    case "Barracks":
                        barracks.add(unit);
                        nbBarracks++;

                        // Consider units in production/training.
                        action = gameState.getUnitAction(unit);
                        if (action != null && action.getType() == UnitAction.TYPE_PRODUCE) {
                            switch (action.getUnitType().name) {
                                case "Light":
                                    if (nbDefenseLights < maxDefenseLights) nbDefenseLights++;
                                    else if (nbAttackLights < maxAttackLights) nbAttackLights++;
                                    break;
                                case "Ranged":
                                    if (nbDefenseRanged < maxDefenseRanged) nbDefenseRanged++;
                                    else if (nbAttackRanged < maxAttackRanged) nbAttackRanged++;
                                    break;
                                case "Heavy":
                                    if (nbDefenseHeavies < maxDefenseHeavies) nbDefenseHeavies++;
                                    else if (nbAttackHeavies < maxAttackHeavies) nbAttackHeavies++;
                                    break;
                            }
                        }
                        break;

                    case "Worker":
                        workerUnits.add(unit);

                        // Account for the Barracks being built.
                        action = gameState.getUnitAction(unit);
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
     * Manages the stance switch of defense units. After spending defenseTime in defense mode, switch defense units to
     * attack, and allow a switchDelay period for units to make the switch. Defense units staying in the perimeter after
     * switchDelay are kept as defense units.
     */
    private void manageDefenseTime() {
        if (gameState.getTime() > 0 && defenseTime > 0 &&
                (gameState.getTime() % defenseTime == 0 || gameState.getTime() % defenseTime < switchDelay)) {
            // Switch all defense units to attack.
            while (defendingUnits.size() > 0) {
                unitDefenseToAttack(defendingUnits.get(0));
            }
        }
    }

    /**
     * Switch the stance of a defense unit, to an attack stance.
     * @param unit
     */
    private void unitDefenseToAttack(Unit unit) {
        if (defendingUnits.contains(unit)) {
            defendingUnits.remove(unit);
            if (!attackingUnits.contains(unit))
                attackingUnits.add(unit);
        }
    }

    /**
     * Select front-line units depending on the mode chosen.
     */
    private void selectFrontLineUnits() {
        switch (frontLineSelectionMode) {
            case (SELECT_FL_BY_OWN_RANGE):
                selectFrontLineUnitsByOwnRange(maxFrontLineUnits, frontLineTacticalDistance);
                break;
            case (SELECT_FL_BY_OPPONENT_RANGE):
                selectFrontLineUnitsByOpponentRange(maxFrontLineUnits, frontLineTacticalDistance);
                break;
        }
    }

    /**
     * Selects own units that have an opponent unit within their attack range.
     * @param maxUnits The maximum number of units to considered.
     * @param tacticalDistance A distance added to the attackRange in order to allow for tactical reasoning apriori.
     */
    private void selectFrontLineUnitsByOwnRange(int maxUnits, int tacticalDistance) {

        for (Unit unit : physicalGameState.getUnits()) {

            if (unit.getPlayer() == playerID && unit.getType().canMove) {

                for (Unit otherUnit : physicalGameState.getUnits()) {
                    if (otherUnit.getPlayer() == 1 - playerID && otherUnit.getType().canMove &&
                        (Math.abs(otherUnit.getX() - unit.getX()) <= unit.getAttackRange() + tacticalDistance &&
                         Math.abs(otherUnit.getY() - unit.getY()) <= unit.getAttackRange() + tacticalDistance)) {

                        if (maxUnits < 0 || frontLineUnits.size() < maxUnits)
                            frontLineUnits.add(unit);
                        break;
                    }
                }
            }

            if (maxUnits >= 0 && frontLineUnits.size() >= maxUnits)
                break;
        }
    }

    /**
     * Selects own units that are in the attack range of an opponent unit.
     * @param maxUnits The maximum number of units to considered.
     * @param tacticalDistance A distance added to the attackRange in order to allow for tactical reasoning apriori.
     */
    private void selectFrontLineUnitsByOpponentRange(int maxUnits, int tacticalDistance) {

        for (Unit unit : physicalGameState.getUnits()) {

            if (unit.getPlayer() == 1 - playerID && unit.getType().canMove) {

                for (Unit otherUnit : physicalGameState.getUnits()) {
                    if (!frontLineUnits.contains(otherUnit) &&
                        otherUnit.getPlayer() == playerID && otherUnit.getType().canMove &&
                        (Math.abs(otherUnit.getX() - unit.getX()) <= unit.getAttackRange() + tacticalDistance &&
                         Math.abs(otherUnit.getY() - unit.getY()) <= unit.getAttackRange() + tacticalDistance))

                        if (maxUnits < 0 || frontLineUnits.size() < maxUnits)
                            frontLineUnits.add(otherUnit);
                        else
                            break;
                }
            }

            if (maxUnits >= 0 && frontLineUnits.size() >= maxUnits)
                break;
        }
    }

    /**
     * The remaining unselected units that are not in the front-line units list, are all considered back units.
     */
    private void selectBackUnits() {

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
     * This method performs what we call heuristic filtering, or pruning, on the set of possible unit-actions of a unit.
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
            filteredActions.addAll(filterBarracksActions(unit, produceLightActions, produceRangedActions,
                        produceHeavyActions, maxProduceActionsChosen));

            filteredActions.add(waitAction);
            return filteredActions;
        }

        // The unit is a base.
        if (bases.contains(unit)) {
            filteredActions.addAll(filterBaseActions(unit, produceWorkerActions, maxProduceActionsChosen));

            filteredActions.add(waitAction);
            return filteredActions;
        }

        return unitActions;
    }

    /**
     * Filter unit-actions of a harvesting worker unit.
     * If the worker can execute a Return or Harvest action, then ignore the rest of action. If the worker can build a
     * Barracks or a Base within the limits, then do so and if it can move, either move towards a resource deposit, or
     * to a base.
     *
     * @param unit The harvesting worker in question.
     * @param harvestActions The possible harvesting actions.
     * @param moveActions The possible move actions.
     * @param produceBarracksActions The possible Barracks building actions.
     * @param produceBaseActions The possible Base building actions.
     * @param maxProduceActionsChosen The maximum number of production actions to chose.
     * @return Filtered unit-actions list.
     */
    private List<UnitAction> filterHarvesterActions(Unit unit, List<UnitAction> harvestActions,List<UnitAction> moveActions,
                                                    List<UnitAction> produceBarracksActions,
                                                    List<UnitAction> produceBaseActions, int maxProduceActionsChosen) {

        List<UnitAction> filteredActions = new LinkedList<>();

        // Harvesting (Return/Harvest) is possible
        if (!harvestActions.isEmpty())
            filteredActions.addAll(harvestActions);
        else {
            // In between the harvesting actions, check if building is possible under the limits.
            if (!produceBarracksActions.isEmpty() && (maxBarracks == -1 || nbBarracks < maxBarracks))
                switch (buildingLocation) {
                    case BUILD_OR_TRAIN_AT_RANDOM_LOCATION:
                        filteredActions.addAll(choseRandomActionsFrom(produceBarracksActions, maxProduceActionsChosen));
                    case BUILD_AWAY_FROM_BASE:
                        filteredActions.addAll(
                                choseBuildingPositionsNonAdjacentToBase(unit, produceBarracksActions, maxProduceActionsChosen));
                }
            else if (!produceBaseActions.isEmpty() && (maxBases == -1 || nbBases < maxBases))
                filteredActions.addAll(choseRandomActionsFrom(produceBaseActions, maxProduceActionsChosen));

            // Move towards the closest base or the closest resource deposit
            if (!moveActions.isEmpty())
                if (random.nextFloat() >= epsilonHarvestMovement)
                    filteredActions.add(getHarvestActions(unit, moveActions));
                else
                    filteredActions.addAll(moveActions);
        }

        if (shuffleActions) Collections.shuffle(filteredActions);
        return filteredActions;
    }

    /**
     * Filter unit-actions of a defense unit.
     * Attack actions are kept, if any. If the unit can move, its movement is restricted to a predefined defense perimeter
     * either rectangular, or circular. Depending on the defense mode, it will either chase units close to base, or close
     * to self. If no perimeter is defined, it will follow the mode's behaviour.
     *
     * @param unit The unit in question.
     * @param moveActions The possible move unit-actions.
     * @param attackActions The possible attack unit-actions.
     * @param horizontalDistance The horizontal distance from base of the rectangular defense perimeter.
     * @param verticalDistance The vertical distance from base of the rectangular defense perimeter.
     * @param radius The distance from base of the circular defense perimeter.
     * @param maxTargets The maximum number of enemy targets.
     * @return Filtered unit-actions list.
     */
    private List<UnitAction> filterDefenseActions(Unit unit, List<UnitAction> moveActions, List<UnitAction> attackActions,
                                                  int horizontalDistance, int verticalDistance, int radius, int maxTargets) {

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
        return filteredActions;
    }

    /**
     * Filter unit-actions of an attack unit.
     * If attacking is possible, add all attack actions and a number (maxEscapes) of move actions, this is to restrict
     * excessive move actions, which may interfere with attacking. If attacking is not possible and the unit can move,
     * then move towards an opponent unit, depending on the chose attack mode.
     *
     * @param unit The unit in question.
     * @param moveActions The possible move actions.
     * @param attackActions The possible attack actions.
     * @param maxTargets The maximum number of units to target.
     * @param maxEscapes The maximum number of escapes allowed.
     * @return A filtered unit-actions list.
     */
    private List<UnitAction> filterAttackActions(Unit unit, List<UnitAction> moveActions,
                                                 List<UnitAction> attackActions, int maxTargets, int maxEscapes) {

        List<UnitAction> filteredActions = new LinkedList<>();

        if (!attackActions.isEmpty()) { // Attacking is possible.
            filteredActions.addAll(attackActions);
            filteredActions.addAll(choseRandomActionsFrom(moveActions, maxEscapes)); // Add all possible move actions (escapes)
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

        if (shuffleActions) Collections.shuffle(filteredActions);
        return filteredActions;
    }

    /**
     * Filter unit-actions of a Barracks unit.
     * If the Barracks can produce a specific unit type, check whether the defense or attack group limits of that
     * unit type are reached or not, if not, allow production.
     *
     * @param unit The barracks in question.
     * @param produceLightActions Possible produce Light actions.
     * @param produceRangedActions Possible produce Ranged actions.
     * @param produceHeavyActions Possible produce Heavy actions.
     * @param maxProduceActionsChosen The maximum number of produce actions to chose, per unit-type.
     * @return A filtered unit-actions list.
     */
    private List<UnitAction> filterBarracksActions(Unit unit, List<UnitAction> produceLightActions, List<UnitAction> produceRangedActions,
                                                   List<UnitAction> produceHeavyActions, int maxProduceActionsChosen) {

        List<UnitAction> filteredActions = new LinkedList<>();

        if (!produceLightActions.isEmpty()) {
            if ((maxDefenseLights == -1 || nbDefenseLights < maxDefenseLights) ||
                    (maxAttackLights == -1 || nbAttackLights < maxAttackLights))
                filteredActions.addAll(selectTrainingActions(unit, produceLightActions, maxProduceActionsChosen));
        }

        if (!produceRangedActions.isEmpty()) {
            if ((maxDefenseRanged == -1 || nbDefenseRanged < maxDefenseRanged) ||
                    (maxAttackRanged == -1 || nbAttackRanged < maxAttackRanged))
                filteredActions.addAll(selectTrainingActions(unit, produceRangedActions, maxProduceActionsChosen));
        }

        if (!produceHeavyActions.isEmpty()) {
            if ((maxDefenseHeavies == -1 || nbDefenseHeavies < maxDefenseHeavies) ||
                    (maxAttackHeavies == -1 || nbAttackHeavies < maxAttackHeavies))
                filteredActions.addAll(selectTrainingActions(unit, produceHeavyActions, maxProduceActionsChosen));
        }

        if (shuffleActions) Collections.shuffle(filteredActions);
        return filteredActions;
    }

    /**
     * Filter unit-actions of a Base unit.
     * If training a worker is possible, then check the harvester/defense/attack limits of workers, and produce if limits
     * are not reached yet.
     *
     * @param unit The Base unit in question.
     * @param produceWorkerActions The produce Worker unit-actions possible.
     * @param maxProduceActionsChosen The maximum number of produce actions to chose.
     * @return A filtered list of unit-actions.
     */
    private List<UnitAction> filterBaseActions(Unit unit, List<UnitAction> produceWorkerActions, int maxProduceActionsChosen) {

        List<UnitAction> filteredActions = new LinkedList<>();

        if (!produceWorkerActions.isEmpty() && // Production/Training is possible. Check limits and perform.
                ((maxHarvesters == -1 || nbHarvesters < maxHarvesters) ||
                 (maxDefenseWorkers == -1 || nbDefenseWorkers < maxDefenseWorkers) ||
                 (maxAttackWorkers == -1 || nbAttackWorkers < maxAttackWorkers)))

            filteredActions.addAll(selectTrainingActions(unit, produceWorkerActions, maxProduceActionsChosen));

        if (shuffleActions) Collections.shuffle(filteredActions);
        return filteredActions;
    }

    /**
     * Selects the production unit-actions, depending on the training location chosen.
     *
     * @param unit The unit (Base or Barracks) in question.
     * @param unitActions The production unit-actions possible.
     * @param maxChoices The maximum number of choices.
     * @return A filtered unit-actions list.
     */
    private List<UnitAction> selectTrainingActions(Unit unit, List<UnitAction> unitActions, int maxChoices) {
        switch (trainingLocation) {
            case BUILD_OR_TRAIN_AT_RANDOM_LOCATION:
                return choseRandomActionsFrom(unitActions, maxChoices);
            case TRAIN_OUTER_SIDE:
                return choseTrainingPositionOnOuterEdges(unit, unitActions, maxChoices);
            default:
                return unitActions;
        }
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
        return getDirectedMoveActions(unit, getClosestOpponentUnitsTo(unit, maxTargets));
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
        if (!bases.isEmpty())
            return getDirectedMoveActions(unit, getClosestOpponentUnitsTo(bases.get(0), maxTargets));
        else
            return getDirectedMoveActions(unit, getClosestOpponentUnitsTo(unit, maxTargets));
    }

    /**
     * Returns a list of move unit-actions targeting enemy units having the lowest HP.
     *
     * @param unit The unit in question.
     * @param maxTargets The maximum number of targets.
     * @return A list of move unit-actions.
     */
    private List<UnitAction> getMoveActionsToLowestHPOpponentUnits(Unit unit, int maxTargets) {
        return getDirectedMoveActions(unit, getLowestHPOpponentUnits(maxTargets));
    }

    /**
     * Returns a list of move unit-actions targeting enemy units having the highest HP.
     *
     * @param unit The unit in question.
     * @param maxTargets The maximum number of targets.
     * @return A list of move unit-actions.
     */
    private List<UnitAction> getMoveActionsToHighestHPOpponentUnits(Unit unit, int maxTargets) {
        return getDirectedMoveActions(unit, getHighestHPOpponentUnits(maxTargets));
    }

    /**
     * Returns a list of move unit-actions targeting random enemy units.
     *
     * @param unit The unit in question.
     * @param maxTargets The maximum number of targets.
     * @return A list of move unit-actions.
     */
    private List<UnitAction> getMoveActionsToRandomOpponentUnits(Unit unit, int maxTargets) {
        return getDirectedMoveActions(unit, getRandomOpponentUnits(maxTargets));
    }

    /**
     * Returns move unit-actions targeting a set of units, using a path-finding algorithm.
     *
     * @param unit The unit in question.
     * @param targetUnits The units to target. (move towards)
     * @return A list of move unit-actions list.
     */
    private List<UnitAction> getDirectedMoveActions(Unit unit, List<Unit> targetUnits) {
        List<UnitAction> directedMoveActions = new LinkedList<>();
        for (Unit targetUnit : targetUnits) {
            int targetPosition = targetUnit.getX() + targetUnit.getY() * physicalGameState.getWidth();
            UnitAction directedMove = pathFinder.findPathToPositionInRange(
                    unit, targetPosition, unit.getAttackRange(), gameState, resourceUsage);
            if (directedMove != null)
                directedMoveActions.add(directedMove);
        }
        return directedMoveActions;
    }

    /**
     * Returns a list of opponent units having the lowest HP.
     *
     * @param maxUnits The maximum number of units to return.
     * @return A list of units.
     */
    private List<Unit> getLowestHPOpponentUnits(int maxUnits) {
        List<Unit> opponentUnits = new LinkedList<>(this.opponentUnits);
        List<Unit> lowestHPOpponentUnits = new LinkedList<>();

        if (opponentUnits.size() > maxUnits) {
            while (lowestHPOpponentUnits.size() < maxUnits) {
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

    /**
     * Returns a list of opponent units having the highest HP.
     *
     * @param maxUnits The maximum number of units to return.
     * @return A list of units.
     */
    private List<Unit> getHighestHPOpponentUnits(int maxUnits) {
        List<Unit> opponentUnits = new LinkedList<>(this.opponentUnits);
        List<Unit> highestHPOpponentUnits = new LinkedList<>();

        if (opponentUnits.size() > maxUnits) {
            while (highestHPOpponentUnits.size() < maxUnits) {
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

    /**
     * Returns a list of random opponent units.
     *
     * @param maxUnits The maximum number of units to return.
     * @return A list of units.
     */
    private List<Unit> getRandomOpponentUnits(int maxUnits) {
        List<Unit> opponentUnits = new LinkedList<>(this.opponentUnits);
        List<Unit> randomOpponentUnits = new LinkedList<>();

        if (opponentUnits.size() > maxUnits) {
            while (randomOpponentUnits.size() < maxUnits) {
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

        // Unit already out. // remove unit from defense group and add to attack group.
        if (unit.getX() > base.getX() + horizontalDistance || unit.getX() < base.getX() - horizontalDistance ||
            unit.getY() > base.getY() + verticalDistance || unit.getY() < base.getY() - verticalDistance) {
            unitDefenseToAttack(unit);
            return moveActions;
        }

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

        //Unit Already out. //remove unit from defense group and add to attack group.
        if (euclideanDistanceNoSqrt(base.getX(), base.getY(), unit.getX(), unit.getY()) > radius * radius) {
            unitDefenseToAttack(unit);
            return moveActions;
        }

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
     * Chose a building position as far as possible from base.
     * @param unit The unit in question. (Worker)
     * @param unitActions The possible produce unit-actions.
     * @param maxChoices The maximum number of locations to chose.
     * @return A unit-action list.
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
     * Chose produce-type unit-actions that use a position having a maximum number of free adjacent cells.
     *
     * @param unit The unit in question (Base or Barracks)
     * @param unitActions The list of produce unit-actions.
     * @param maxChoices The maximum number of choices to return.
     * @return A list of unit-actions.
     */
    private List<UnitAction> choseTrainingPositionOnOuterEdges(Unit unit, List<UnitAction> unitActions, int maxChoices) {

        if (maxChoices == -1 || maxChoices >= unitActions.size())
            return unitActions;

        List<UnitAction> actions = new LinkedList<>(unitActions);
        List<UnitAction> chosenActions = new LinkedList<>();

        for (int counter = 0; counter < maxChoices; counter++) {
            UnitAction bestAction = null;
            int maxFreeCells = 0;
            int freeCells = 0;

            for (UnitAction action : actions) {
                switch (action.getDirection()) {
                    case UnitAction.DIRECTION_UP:
                        freeCells = freeAdjacentCells(unit.getX(), unit.getY() - 1);
                        break;
                    case UnitAction.DIRECTION_RIGHT:
                        freeCells = freeAdjacentCells(unit.getX() + 1, unit.getY());
                        break;
                    case UnitAction.DIRECTION_DOWN:
                        freeCells = freeAdjacentCells(unit.getX(), unit.getY() + 1);
                        break;
                    case UnitAction.DIRECTION_LEFT:
                        freeCells = freeAdjacentCells(unit.getX() - 1, unit.getY());
                        break;
                }

                if (bestAction == null || freeCells > maxFreeCells) {
                    bestAction = action;
                    maxFreeCells = freeCells;
                }
            }

            chosenActions.add(bestAction);
            actions.remove(bestAction);
        }

        return chosenActions;
    }

    /**
     * Returns the number of free cells adjacent to the provided cell.
     * @param x The x-coordinates of the cell in question.
     * @param y The y-coordinates of the cell in question.
     * @return The number of free adjacent cells.
     */
    private int freeAdjacentCells(int x, int y) {
        int cellCount = 0;
        boolean[][] freeCells = physicalGameState.getAllFree();

        if (y - 1 >= 0 && freeCells[x][y - 1]) cellCount++;
        if (x + 1 < physicalGameState.getWidth() && freeCells[x + 1][y]) cellCount++;
        if (y + 1 < physicalGameState.getHeight() && freeCells[x][y + 1]) cellCount++;
        if (x - 1 >= 0 && freeCells[x - 1][y]) cellCount++;

        return cellCount;
    }

    /**
     * Selects a number of random actions from a list of given unit actions.
     * @param unitActions The list of unit-actions to chose from.
     * @param maxChoices The maximum number of actions to chose.
     * @return A unit-actions list.
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

    /**
     * Returns a list of opponent units around a given unit, in the immediate square range.
     *
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

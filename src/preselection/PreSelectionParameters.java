package preselection;

import ai.abstraction.pathfinding.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * A class that defines all the action preselection parameters of Parametric NMCTS.
 */
public class PreSelectionParameters {

    // PATH FINDERS:
    public static final int A_STAR_PF = 0, FLOOD_FILL_PF = 1, BFS_PF = 2, GREEDY_PF = 3;

    // THE DESIRED ARMY COMPOSITION ***********************************************************************************
    // The limits imposed on the number of possible units, of each type.
    // 0 : do not produce, -1 : produce infinitely, n : produce n at most.
    int maxBases = 1;          // The number of Bases to build, at most.
    int maxBarracks = 1;       // The number of Barracks to build, at most.
    int maxHarvesters = 1;     // The number of harvesting Workers to train, at most.
    int maxOffenseWorkers = -1; // The number of offense Workers to train, at most.
    int maxOffenseLights = 0;  // The number of offense Lights to train, at most.
    int maxOffenseRanged = 0;  // The number of offense Ranged to train, at most.
    int maxOffenseHeavies = 0; // The number of offense Heavies to train, at most.
    int maxDefenseWorkers = 0; // The number of defense Workers to train, at most.
    int maxDefenseLights = 3;  // The number of defense Lights to train, at most.
    int maxDefenseRanged = -1; // The number of defense Ranged to train, at most.
    int maxDefenseHeavies = 2; // The number of defense Heavies to train, at most.

    // Unit assignment priority, either assign Defense units first or Offense units first.
    int priority = FunctionalGroupsMonitor.DEFENSE_PRIORITY;

    // DEFENSE : parameters defining the defense behavior. ************************************************************
    // Rectangular defense perimeter: If both vertical and horizontal distances are > 0, a rectangular defense perimeter
    // is set, otherwise, it is disabled and a circular defense perimeter may take its place.
    // The horizontal distance from base.
    int horizontalDistanceFromBase = 0;
    // The vertical distance from base.
    int verticalDistanceFromBase = 0;

    // Circular defense perimeter: if the radius is > 0, a circular defense perimeter is set, otherwise, it is disabled.
    // If both perimeter types dimensions are set to 0, defense is disabled.
    // The defense radius from base.
    int radiusFromBase = 7;

    // The maximum number of enemy units to target on defense mode.
    int maxTargetsOnDefense = 2;

    // The defense mode in place. Whom to attack while defending, either base attackers or self attackers.
    int defenseMode = DefenseManager.DEFEND_BASE;

    // The maximum number of cycles defense units will stay inside the perimeter. -1: stay indefinitely.
    // After each defense period, a stance switch is triggered, from def to attack. -1: disabled.
    int timeTriggerDefensePeriod = -1;
    // The amount of time after which defense units are allowed to switch stance, after the time trigger.
    int timeTriggerSwitchDelay = 200;
    // The number of defense units that triggers a DefToOff switch when reached. -1: disabled.
    int unitCountTriggerThreshold = -1;
    // The additional score percent needed by the player, above the opponent's score, to trigger a switch.
    // -1: disabled, 0: the minimum score greater than the opponent's score.
    float scoreTriggerOverpowerFactor = 0.25f;
    // The value of assault units with respect to worker units when calculating the trigger score.
    int scoreTriggerAssaultUnitValue = 4;
    // The number of units concerned by the stance switch, -1: switch all defense units.
    int switchingUnitsCount = -1;

    // OFFENSE : parameters defining the attack behavior. ************************************************************
    // The maximum number of enemy units to target on attack mode.
    int maxTargetsOnOffense = 2;
    // If a unit can attack and also move, maxEscapes limit the number of move actions allowed
    int maxEscapes = 1;
    // The attack mode in use.
    int offenseTargetMode = OffenseManager.TARGET_CLOSEST;

    // A fixed target that is always added to the targeted units. Can be the opponent base or barracks.
    int fixedTarget = OffenseManager.FIXED_TARGET_ALL_STRUCTURES;

    // PRODUCTION : Building / Training ******************************************************************************
    // The Barracks building location. HARVESTERS:------------------------------
    int buildLocation = HarvestManager.BUILD_AT_ISOLATED_LOCATION;
    // If a unit can produce, limit the number of produce actions to chose from.
    int maxBuildActionsChosen = 2;

    // In case isolated build location is activated, this parameter defines the radius to scan around the future build
    // location.
    int isolatedBuildScanRadius = 1;
    // Indicates the maximum number of occupied cells tolerated in the scanned region.
    int isolatedBuildMaxOccupiedCells = 1;

    // The mobile units training location. STRUCTURES:--------------------------
    int trainSide = TrainingManager.TRAIN_AT_ISOLATED_SIDE;
    // Limit of the number of training actions to chose from.
    int maxTrainActionsChosen = 2;

    // In case isolated train location is activated, this parameter defines the width of the zone to scan from either
    // sides of the future train location.
    int isolatedTrainScanWidth = 1;
    // Defines the depth of the scanned zone ahead of the future train location.
    int isolatedTrainScanDepth = 2;
    // Indicates the maximum number of occupied cells tolerated in the scanned region.
    int isolatedTrainMaxOccupiedCells = 2;

    // Exploration parameter for movements. 1 : Random, 0 : Focused (using path finding) *****************************
    // The exploration parameter of harvesting units.
    float epsilonHarvestMovement = 0.0f;
    // The exploration parameter of defense units.
    float epsilonDefenseMovement = 0.05f;
    // The exploration parameter of offense units.
    float epsilonOffenseMovement = 0.05f;

    // Front Line tactics, -1 maxFontLineUnits means take all units in the front-line ********************************
    // Front-Line selection method.
    int frontLineSelectionMode = SituationalGroupsMonitor.SELECT_BY_PLAYER_RANGE;
    // Maximum number of front-line units to consider. -1: take all, 0: take none.
    int maxFrontLineUnits = 3;
    // The tactical distance ahead of attack range.
    int frontLineTacticalDistance = 1;
    // Wait duration of front-line units.
    int frontLineWaitDuration = 3;

    // Other Parameters.
    // Wait duration of non front-line units.
    int defaultWaitDuration = 10;
    // Shuffles the returned actions.
    boolean shuffleActions = false;

    // The path finding algorithm used to direct movement, for each unit group.
    PathFinding harvestPathFinder = new AStarPathFinding(); /*new FloodFillPathFinding();*/ /*new BFSPathFinding();*/ /*new GreedyPathFinding();*/
    PathFinding defensePathFinder = new FloodFillPathFinding();
    PathFinding offensePathFinder = new AStarPathFinding();
    PathFinding frontLinePathFinder = new FloodFillPathFinding();

    /**
     * Default constructor, creates a PreSelectionParameters instance with the default parameter values.
     */
    public PreSelectionParameters() {}

    public PreSelectionParameters(int defaultWaitDuration, boolean shuffleActions) {
        this.defaultWaitDuration = defaultWaitDuration;
        this.shuffleActions = shuffleActions;
    }

    public void setUnitComposition(int maxBases, int maxBarracks,  int maxHarvesters,
                                   int maxOffenseWorkers, int maxOffenseLights, int maxOffenseRanged, int maxOffenseHeavies,
                                   int maxDefenseWorkers, int maxDefenseLights, int maxDefenseRanged, int maxDefenseHeavies,
                                   int priority) {
        this.maxBases = maxBases;
        this.maxBarracks = maxBarracks;
        this.maxHarvesters = maxHarvesters;
        this.maxOffenseWorkers = maxOffenseWorkers;
        this.maxOffenseLights = maxOffenseLights;
        this.maxOffenseRanged = maxOffenseRanged;
        this.maxOffenseHeavies = maxOffenseHeavies;
        this.maxDefenseWorkers = maxDefenseWorkers;
        this.maxDefenseLights = maxDefenseLights;
        this.maxDefenseRanged = maxDefenseRanged;
        this.maxDefenseHeavies = maxDefenseHeavies;
        this.priority = priority;
    }

    public void setDefense(int horizontalDistanceFromBase, int verticalDistanceFromBase, int radiusFromBase,
                           int maxTargetsOnDefense, int defenseMode, int defensePathFinderIndex,
                           float epsilonDefenseMovement) {
        this.horizontalDistanceFromBase = horizontalDistanceFromBase;
        this.verticalDistanceFromBase = verticalDistanceFromBase;
        this.radiusFromBase = radiusFromBase;
        this.maxTargetsOnDefense = maxTargetsOnDefense;
        this.defenseMode = defenseMode;
        this.epsilonDefenseMovement = epsilonDefenseMovement;
        this.defensePathFinder = getPathFinder(defensePathFinderIndex);
    }

    public void setDefenseSwitch(int timeTriggerDefensePeriod, int timeTriggerSwitchDelay,
                                 int unitCountTriggerThreshold, float scoreTriggerOverpowerFactor,
                                 int scoreTriggerAssaultUnitValue, int switchingUnitsCount) {
        this.timeTriggerDefensePeriod = timeTriggerDefensePeriod;
        this.timeTriggerSwitchDelay = timeTriggerSwitchDelay;
        this.unitCountTriggerThreshold = unitCountTriggerThreshold;
        this.scoreTriggerOverpowerFactor = scoreTriggerOverpowerFactor;
        this.scoreTriggerAssaultUnitValue = scoreTriggerAssaultUnitValue;
        this.switchingUnitsCount = switchingUnitsCount;
    }

    public void setOffense(int maxTargetsOnOffense, int maxEscapes, int offenseTargetMode,
                           int fixedTarget, int offensePathFinderIndex, float epsilonOffenseMovement) {
        this.maxTargetsOnOffense = maxTargetsOnOffense;
        this.maxEscapes = maxEscapes;
        this.offenseTargetMode = offenseTargetMode;
        this.fixedTarget = fixedTarget;
        this.offensePathFinder = getPathFinder(offensePathFinderIndex);
        this.epsilonOffenseMovement = epsilonOffenseMovement;
    }

    public void setHarvest(float epsilonHarvestMovement, int harvestPathFinderIndex) {
        this.epsilonHarvestMovement = epsilonHarvestMovement;
        this.harvestPathFinder = getPathFinder(harvestPathFinderIndex);
    }

    public void setBuilding(int buildLocation, int maxBuildActionsChosen, int isolatedBuildScanRadius,
                            int isolatedBuildMaxOccupiedCells) {

        this.buildLocation = buildLocation;
        this.maxBuildActionsChosen = maxBuildActionsChosen;
        this.isolatedBuildScanRadius = isolatedBuildScanRadius;
        this.isolatedBuildMaxOccupiedCells = isolatedBuildMaxOccupiedCells;
    }

    public void setTraining(int trainSide, int maxTrainActionsChosen, int isolatedTrainScanWidth,
                            int isolatedTrainScanDepth, int isolatedTrainMaxOccupiedCells) {
        this.trainSide = trainSide;
        this.maxTrainActionsChosen = maxTrainActionsChosen;
        this.isolatedTrainScanWidth = isolatedTrainScanWidth;
        this.isolatedTrainScanDepth = isolatedTrainScanDepth;
        this.isolatedTrainMaxOccupiedCells = isolatedTrainMaxOccupiedCells;
    }

    public void setFrontLine(int frontLineSelectionMode, int maxFrontLineUnits, int frontLineTacticalDistance,
                             int frontLineWaitDuration, int frontLinePathFinderIndex) {
        this.frontLineSelectionMode = frontLineSelectionMode;
        this.maxFrontLineUnits = maxFrontLineUnits;
        this.frontLineTacticalDistance = frontLineTacticalDistance;
        this.frontLineWaitDuration = frontLineWaitDuration;
        this.frontLinePathFinder = getPathFinder(frontLinePathFinderIndex);
    }

    private PathFinding getPathFinder(int pathFinderIndex) {
        switch (pathFinderIndex) {
            case A_STAR_PF:
                return new AStarPathFinding();
            case FLOOD_FILL_PF:
                return new FloodFillPathFinding();
            case BFS_PF:
                return new BFSPathFinding();
            case GREEDY_PF:
                return new GreedyPathFinding();
        }
        return new AStarPathFinding();
    }

    private int getPathFinderIndex(PathFinding pathFinder) {
        switch (pathFinder.toString()) {
            case "AStarPathFinding": return 0;
            case "FloodFillPathFinding": return 1;
            case "BFSPathFinding": return 2;
            case "GreedyPathFinding": return 3;
        }
        return 0;
    }

    public String toString() {
        String output = "[UNITS]:  maxBases:" + maxBases + " maxBarracks:" + maxBarracks +
                " maxHarvesters:" + maxHarvesters + " maxOffenseWorkers:" + maxOffenseWorkers +
                " maxOffenseLights:" + maxOffenseLights + " maxOffenseRanged:" + maxOffenseRanged +
                " maxOffenseHeavies:" + maxOffenseHeavies + " maxDefenseWorkers:" + maxDefenseWorkers +
                " maxDefenseLights:" + maxDefenseLights + " maxDefenseRanged:" + maxDefenseRanged +
                " maxDefenseHeavies:" + maxDefenseHeavies + "\n          priority: " +
                (priority == FunctionalGroupsMonitor.DEFENSE_PRIORITY ? "DEFENSE(0)" : "OFFENSE(1)") +
                " defaultWaitDuration:" + defaultWaitDuration;

        output += "\n[DEF]:    horizontalDistanceFromBase:" + horizontalDistanceFromBase + " verticalDistanceFromBase:" + verticalDistanceFromBase +
                " radiusFromBase:" + radiusFromBase + " maxTargetsOnDefense:" + maxTargetsOnDefense + " defenseMode:" +
                (defenseMode == DefenseManager.DEFEND_BASE ? "DEFEND_BASE(0)" : "DEFEND_SELF(1)") + " defensePathFinder:" + defensePathFinder.toString();

        output += "\n[DEF SW]: timeTriggerDefensePeriod:" + timeTriggerDefensePeriod + " timeTriggerSwitchDelay:" + timeTriggerSwitchDelay +
                " unitCountTriggerThreshold:" + unitCountTriggerThreshold + " scoreTriggerOverpowerFactor:" + scoreTriggerOverpowerFactor +
                " switchingUnitsCount:" + switchingUnitsCount;

        output += "\n[OFF]:    maxTargetsOnOffense:" + maxTargetsOnOffense + " maxEscapes:" + maxEscapes +
                " offenseTargetMode:" + offenseModeToStr() + " fixedTarget:" + fixedTargetToStr() +
                " offensePathFinder:" + offensePathFinder.toString();

        output += "\n[HRV]:    harvestPathFinder:" + harvestPathFinder.toString();

        output += "\n[BLD]:    buildLocation:" + (buildLocation == HarvestManager.BUILD_AT_RANDOM_LOCATION ? "RANDOM(0)" : "ISOLATED(1)") +
                " maxBuildActions:" + maxBuildActionsChosen + " isolatedBuildScanRadius:" + isolatedBuildScanRadius +
                " isolatedBuildMaxOccupiedCells:" + isolatedBuildMaxOccupiedCells;

        output += "\n[TRN]:    trainSide:" + (trainSide == TrainingManager.TRAIN_AT_RANDOM_SIDE ? "RANDOM(0)" : "ISOLATED(1)") + " maxTrainActions:" + maxTrainActionsChosen +
                " isolatedTrainScanWidth:" + isolatedTrainScanWidth + " isolatedTrainScanDepth:" + isolatedTrainScanDepth +
                " isolatedTrainMaxOccupiedCells:" + isolatedTrainMaxOccupiedCells;

        output += "\n[FL]:     frontLineSelectionMode:" + (frontLineSelectionMode == SituationalGroupsMonitor.SELECT_BY_PLAYER_RANGE ? "PLAYER_RANGE(0)" : "OPPONENT_RANGE(1)") + " maxFrontLineUnits:" + maxFrontLineUnits +
                " frontLineTacticalDistance:" + frontLineTacticalDistance + " frontLineWaitDuration:" + frontLineWaitDuration +
                " frontLinePathFinder:" + frontLinePathFinder;

        return output;
    }

    private String offenseModeToStr() {
        switch (offenseTargetMode) {
            case OffenseManager.TARGET_CLOSEST: return "CLOSEST(0)";
            case OffenseManager.TARGET_CLOSEST_TO_BASE: return "CLOSEST_TO_BASE(1)";
            case OffenseManager.TARGET_MIN_HP: return "MIN_HP(2)";
            case OffenseManager.TARGET_MAX_HP: return "MAX_HP(3)";
            case OffenseManager.TARGET_RANDOM: return "RANDOM(4)";
        }
        return "NONE";
    }

    private String fixedTargetToStr() {
        switch (fixedTarget) {
            case OffenseManager.NO_FIXED_TARGET: return "NONE(0)";
            case OffenseManager.FIXED_TARGET_BASE_FIRST: return "BASE_FIRST(1)";
            case OffenseManager.FIXED_TARGET_BARRACKS_FIRST: return "BARRACKS_FIRST(2)";
            case OffenseManager.FIXED_TARGET_ALL_STRUCTURES: return "ALL_STRUCTURES(3)";
        }
        return "NONE";
    }
    
    public PreSelectionParameters clone() {
        PreSelectionParameters clone = new PreSelectionParameters();
        clone.maxBases = this.maxBases;
        clone.maxBarracks = this.maxBarracks;
        clone.maxHarvesters = this.maxHarvesters;
        clone.maxOffenseWorkers = this.maxOffenseWorkers;
        clone.maxOffenseLights = this.maxOffenseLights;
        clone.maxOffenseRanged = this.maxOffenseRanged;
        clone.maxOffenseHeavies = this.maxOffenseHeavies;
        clone.maxDefenseWorkers = this.maxDefenseWorkers;
        clone.maxDefenseLights = this.maxDefenseLights;
        clone.maxDefenseRanged = this.maxDefenseRanged;
        clone.maxDefenseHeavies = this.maxDefenseHeavies;
        clone.priority = this.priority;

        clone.horizontalDistanceFromBase = this.horizontalDistanceFromBase;
        clone.verticalDistanceFromBase = this.verticalDistanceFromBase;
        clone.radiusFromBase = this.radiusFromBase;
        clone.maxTargetsOnDefense = this.maxTargetsOnDefense;
        clone.defenseMode = this.defenseMode;

        clone.timeTriggerDefensePeriod = this.timeTriggerDefensePeriod;
        clone.timeTriggerSwitchDelay = this.timeTriggerSwitchDelay;
        clone.unitCountTriggerThreshold = this.unitCountTriggerThreshold;
        clone.scoreTriggerOverpowerFactor = this.scoreTriggerOverpowerFactor;
        clone.scoreTriggerAssaultUnitValue = this.scoreTriggerAssaultUnitValue;
        clone.switchingUnitsCount = this.switchingUnitsCount;

        clone.maxTargetsOnOffense = this.maxTargetsOnOffense;
        clone.maxEscapes = this.maxEscapes;
        clone.offenseTargetMode = this.offenseTargetMode;
        clone.fixedTarget = this.fixedTarget;

        clone.buildLocation = this.buildLocation;
        clone.maxBuildActionsChosen = this.maxBuildActionsChosen;
        clone.isolatedBuildScanRadius = this.isolatedBuildScanRadius;
        clone.isolatedBuildMaxOccupiedCells = this.isolatedBuildMaxOccupiedCells;

        clone.trainSide = this.trainSide;
        clone.maxTrainActionsChosen = this.maxTrainActionsChosen;
        clone.isolatedTrainScanWidth = this.isolatedTrainScanWidth;
        clone.isolatedTrainScanDepth = this.isolatedTrainScanDepth;
        clone.isolatedTrainMaxOccupiedCells = this.isolatedTrainMaxOccupiedCells;

        clone.epsilonHarvestMovement = this.epsilonHarvestMovement;
        clone.epsilonOffenseMovement = this.epsilonOffenseMovement;
        clone.epsilonDefenseMovement = this.epsilonDefenseMovement;

        clone.frontLineSelectionMode = this.frontLineSelectionMode;
        clone.maxFrontLineUnits = this.maxFrontLineUnits;
        clone.frontLineTacticalDistance = this.frontLineTacticalDistance;
        clone.frontLineWaitDuration = this.frontLineWaitDuration;
        clone.defaultWaitDuration = this.defaultWaitDuration;

        clone.shuffleActions = this.shuffleActions;

        try {
            clone.harvestPathFinder = this.harvestPathFinder.getClass().getConstructor().newInstance();
            clone.offensePathFinder = this.offensePathFinder.getClass().getConstructor().newInstance();
            clone.defensePathFinder = this.defensePathFinder.getClass().getConstructor().newInstance();
            clone.frontLinePathFinder = this.frontLinePathFinder.getClass().getConstructor().newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return clone;
    }

    public String toJSONStr() throws Exception {
        return new ObjectMapper().writeValueAsString(this);
    }

    public static PreSelectionParameters fromJSON(String json) throws Exception {
        return new ObjectMapper().readValue(json, PreSelectionParameters.class);
    }

    public int getMaxBases() {
        return maxBases;
    }

    public void setMaxBases(int maxBases) {
        this.maxBases = maxBases;
    }

    public int getMaxBarracks() {
        return maxBarracks;
    }

    public void setMaxBarracks(int maxBarracks) {
        this.maxBarracks = maxBarracks;
    }

    public int getMaxHarvesters() {
        return maxHarvesters;
    }

    public void setMaxHarvesters(int maxHarvesters) {
        this.maxHarvesters = maxHarvesters;
    }

    public int getMaxOffenseWorkers() {
        return maxOffenseWorkers;
    }

    public void setMaxOffenseWorkers(int maxOffenseWorkers) {
        this.maxOffenseWorkers = maxOffenseWorkers;
    }

    public int getMaxOffenseLights() {
        return maxOffenseLights;
    }

    public void setMaxOffenseLights(int maxOffenseLights) {
        this.maxOffenseLights = maxOffenseLights;
    }

    public int getMaxOffenseRanged() {
        return maxOffenseRanged;
    }

    public void setMaxOffenseRanged(int maxOffenseRanged) {
        this.maxOffenseRanged = maxOffenseRanged;
    }

    public int getMaxOffenseHeavies() {
        return maxOffenseHeavies;
    }

    public void setMaxOffenseHeavies(int maxOffenseHeavies) {
        this.maxOffenseHeavies = maxOffenseHeavies;
    }

    public int getMaxDefenseWorkers() {
        return maxDefenseWorkers;
    }

    public void setMaxDefenseWorkers(int maxDefenseWorkers) {
        this.maxDefenseWorkers = maxDefenseWorkers;
    }

    public int getMaxDefenseLights() {
        return maxDefenseLights;
    }

    public void setMaxDefenseLights(int maxDefenseLights) {
        this.maxDefenseLights = maxDefenseLights;
    }

    public int getMaxDefenseRanged() {
        return maxDefenseRanged;
    }

    public void setMaxDefenseRanged(int maxDefenseRanged) {
        this.maxDefenseRanged = maxDefenseRanged;
    }

    public int getMaxDefenseHeavies() {
        return maxDefenseHeavies;
    }

    public void setMaxDefenseHeavies(int maxDefenseHeavies) {
        this.maxDefenseHeavies = maxDefenseHeavies;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getHorizontalDistanceFromBase() {
        return horizontalDistanceFromBase;
    }

    public void setHorizontalDistanceFromBase(int horizontalDistanceFromBase) {
        this.horizontalDistanceFromBase = horizontalDistanceFromBase;
    }

    public int getVerticalDistanceFromBase() {
        return verticalDistanceFromBase;
    }

    public void setVerticalDistanceFromBase(int verticalDistanceFromBase) {
        this.verticalDistanceFromBase = verticalDistanceFromBase;
    }

    public int getRadiusFromBase() {
        return radiusFromBase;
    }

    public void setRadiusFromBase(int radiusFromBase) {
        this.radiusFromBase = radiusFromBase;
    }

    public int getMaxTargetsOnDefense() {
        return maxTargetsOnDefense;
    }

    public void setMaxTargetsOnDefense(int maxTargetsOnDefense) {
        this.maxTargetsOnDefense = maxTargetsOnDefense;
    }

    public int getDefenseMode() {
        return defenseMode;
    }

    public void setDefenseMode(int defenseMode) {
        this.defenseMode = defenseMode;
    }

    public int getTimeTriggerDefensePeriod() {
        return timeTriggerDefensePeriod;
    }

    public void setTimeTriggerDefensePeriod(int timeTriggerDefensePeriod) {
        this.timeTriggerDefensePeriod = timeTriggerDefensePeriod;
    }

    public int getTimeTriggerSwitchDelay() {
        return timeTriggerSwitchDelay;
    }

    public void setTimeTriggerSwitchDelay(int timeTriggerSwitchDelay) {
        this.timeTriggerSwitchDelay = timeTriggerSwitchDelay;
    }

    public int getUnitCountTriggerThreshold() {
        return unitCountTriggerThreshold;
    }

    public void setUnitCountTriggerThreshold(int unitCountTriggerThreshold) {
        this.unitCountTriggerThreshold = unitCountTriggerThreshold;
    }

    public float getScoreTriggerOverpowerFactor() {
        return scoreTriggerOverpowerFactor;
    }

    public void setScoreTriggerOverpowerFactor(float scoreTriggerOverpowerFactor) {
        this.scoreTriggerOverpowerFactor = scoreTriggerOverpowerFactor;
    }

    public int getScoreTriggerAssaultUnitValue() {
        return scoreTriggerAssaultUnitValue;
    }

    public void setScoreTriggerAssaultUnitValue(int scoreTriggerAssaultUnitValue) {
        this.scoreTriggerAssaultUnitValue = scoreTriggerAssaultUnitValue;
    }

    public int getSwitchingUnitsCount() {
        return switchingUnitsCount;
    }

    public void setSwitchingUnitsCount(int switchingUnitsCount) {
        this.switchingUnitsCount = switchingUnitsCount;
    }

    public int getMaxTargetsOnOffense() {
        return maxTargetsOnOffense;
    }

    public void setMaxTargetsOnOffense(int maxTargetsOnOffense) {
        this.maxTargetsOnOffense = maxTargetsOnOffense;
    }

    public int getMaxEscapes() {
        return maxEscapes;
    }

    public void setMaxEscapes(int maxEscapes) {
        this.maxEscapes = maxEscapes;
    }

    public int getOffenseTargetMode() {
        return offenseTargetMode;
    }

    public void setOffenseTargetMode(int offenseTargetMode) {
        this.offenseTargetMode = offenseTargetMode;
    }

    public int getFixedTarget() {
        return fixedTarget;
    }

    public void setFixedTarget(int fixedTarget) {
        this.fixedTarget = fixedTarget;
    }

    public int getBuildLocation() {
        return buildLocation;
    }

    public void setBuildLocation(int buildLocation) {
        this.buildLocation = buildLocation;
    }

    public int getMaxBuildActionsChosen() {
        return maxBuildActionsChosen;
    }

    public void setMaxBuildActionsChosen(int maxBuildActionsChosen) {
        this.maxBuildActionsChosen = maxBuildActionsChosen;
    }

    public int getIsolatedBuildScanRadius() {
        return isolatedBuildScanRadius;
    }

    public void setIsolatedBuildScanRadius(int isolatedBuildScanRadius) {
        this.isolatedBuildScanRadius = isolatedBuildScanRadius;
    }

    public int getIsolatedBuildMaxOccupiedCells() {
        return isolatedBuildMaxOccupiedCells;
    }

    public void setIsolatedBuildMaxOccupiedCells(int isolatedBuildMaxOccupiedCells) {
        this.isolatedBuildMaxOccupiedCells = isolatedBuildMaxOccupiedCells;
    }

    public int getTrainSide() {
        return trainSide;
    }

    public void setTrainSide(int trainSide) {
        this.trainSide = trainSide;
    }

    public int getMaxTrainActionsChosen() {
        return maxTrainActionsChosen;
    }

    public void setMaxTrainActionsChosen(int maxTrainActionsChosen) {
        this.maxTrainActionsChosen = maxTrainActionsChosen;
    }

    public int getIsolatedTrainScanWidth() {
        return isolatedTrainScanWidth;
    }

    public void setIsolatedTrainScanWidth(int isolatedTrainScanWidth) {
        this.isolatedTrainScanWidth = isolatedTrainScanWidth;
    }

    public int getIsolatedTrainScanDepth() {
        return isolatedTrainScanDepth;
    }

    public void setIsolatedTrainScanDepth(int isolatedTrainScanDepth) {
        this.isolatedTrainScanDepth = isolatedTrainScanDepth;
    }

    public int getIsolatedTrainMaxOccupiedCells() {
        return isolatedTrainMaxOccupiedCells;
    }

    public void setIsolatedTrainMaxOccupiedCells(int isolatedTrainMaxOccupiedCells) {
        this.isolatedTrainMaxOccupiedCells = isolatedTrainMaxOccupiedCells;
    }

    public float getEpsilonHarvestMovement() {
        return epsilonHarvestMovement;
    }

    public void setEpsilonHarvestMovement(float epsilonHarvestMovement) {
        this.epsilonHarvestMovement = epsilonHarvestMovement;
    }

    public float getEpsilonDefenseMovement() {
        return epsilonDefenseMovement;
    }

    public void setEpsilonDefenseMovement(float epsilonDefenseMovement) {
        this.epsilonDefenseMovement = epsilonDefenseMovement;
    }

    public float getEpsilonOffenseMovement() {
        return epsilonOffenseMovement;
    }

    public void setEpsilonOffenseMovement(float epsilonOffenseMovement) {
        this.epsilonOffenseMovement = epsilonOffenseMovement;
    }

    public int getFrontLineSelectionMode() {
        return frontLineSelectionMode;
    }

    public void setFrontLineSelectionMode(int frontLineSelectionMode) {
        this.frontLineSelectionMode = frontLineSelectionMode;
    }

    public int getMaxFrontLineUnits() {
        return maxFrontLineUnits;
    }

    public void setMaxFrontLineUnits(int maxFrontLineUnits) {
        this.maxFrontLineUnits = maxFrontLineUnits;
    }

    public int getFrontLineTacticalDistance() {
        return frontLineTacticalDistance;
    }

    public void setFrontLineTacticalDistance(int frontLineTacticalDistance) {
        this.frontLineTacticalDistance = frontLineTacticalDistance;
    }

    public int getFrontLineWaitDuration() {
        return frontLineWaitDuration;
    }

    public void setFrontLineWaitDuration(int frontLineWaitDuration) {
        this.frontLineWaitDuration = frontLineWaitDuration;
    }

    public int getDefaultWaitDuration() {
        return defaultWaitDuration;
    }

    public void setDefaultWaitDuration(int defaultWaitDuration) {
        this.defaultWaitDuration = defaultWaitDuration;
    }

    public boolean isShuffleActions() {
        return shuffleActions;
    }

    public void setShuffleActions(boolean shuffleActions) {
        this.shuffleActions = shuffleActions;
    }

    public int getHarvestPathFinder() {
        return getPathFinderIndex(this.harvestPathFinder);
    }

    public void setHarvestPathFinder(int harvestPathFinderIndex) {
        this.harvestPathFinder = getPathFinder(harvestPathFinderIndex);
    }

    public int getDefensePathFinder() {
        return getPathFinderIndex(this.defensePathFinder);
    }

    public void setDefensePathFinder(int defensePathFinderIndex) {
        this.defensePathFinder = getPathFinder(defensePathFinderIndex);
    }

    public int getOffensePathFinder() {
        return getPathFinderIndex(this.offensePathFinder);
    }

    public void setOffensePathFinder(int offensePathFinderIndex) {
        this.offensePathFinder = getPathFinder(offensePathFinderIndex);
    }

    public int getFrontLinePathFinder() {
        return getPathFinderIndex(this.frontLinePathFinder);
    }

    public void setFrontLinePathFinder(int frontLinePathFinderIndex) {
        this.frontLinePathFinder = getPathFinder(frontLinePathFinderIndex);
    }

}

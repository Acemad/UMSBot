package preselection;

import ai.abstraction.pathfinding.*;

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
    int maxBarracks = 2 ;      // The number of Barracks to build, at most.
    int maxHarvesters = 3;     // The number of harvesting Workers to train, at most.
    int maxOffenseWorkers = 4; // The number of offense Workers to train, at most.
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
    // The amount of time in which defense units are allowed to switch stance, after the time trigger.
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

    public void setOffense(int maxTargetsOnOffense, int maxEscapes, int offenseTargetMode, int fixedTarget,
                           int offensePathFinderIndex, float epsilonOffenseMovement) {
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

    private PathFinding getPathFinder(int defensePathFinderIndex) {
        switch (defensePathFinderIndex) {
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
}

package preselection;

import rts.units.Unit;

import java.util.LinkedList;
import java.util.List;

/**
 * Groups units according to their functions. Assigns each unit to its respective group, under the limits imposed by the
 * maximum counts.
 *
 * @author Acemad
 */
public class FunctionalGroupsMonitor {

    // Assignment priority ********************************************************************************************
    public static final int DEFENSE_PRIORITY = 0; // Assign defense units first.
    public static final int OFFENSE_PRIORITY = 1; // Assign offense units first.

    StateMonitor stateMonitor;

    // The functional groups.
    List<Unit> harvestUnits = new LinkedList<>();
    List<Unit> defenseUnits = new LinkedList<>();
    List<Unit> offenseUnits = new LinkedList<>();

    // The functional unit-type groups.
    List<Unit> defenseWorkers = new LinkedList<>();
    List<Unit> defenseLights = new LinkedList<>();
    List<Unit> defenseRanged = new LinkedList<>();
    List<Unit> defenseHeavies = new LinkedList<>();

    List<Unit> offenseWorkers = new LinkedList<>();
    List<Unit> offenseLights = new LinkedList<>();
    List<Unit> offenseRanged = new LinkedList<>();
    List<Unit> offenseHeavies = new LinkedList<>();


    public FunctionalGroupsMonitor(StateMonitor stateMonitor, int priority, int maxHarvesters,
                                   int maxDefenseWorkers, int maxDefenseLights, int maxDefenseRanged, int maxDefenseHeavies,
                                   int maxOffenseWorkers, int maxOffenseLights, int maxOffenseRanged, int maxOffenseHeavies) {

        this.stateMonitor = stateMonitor;

        assignHarvestUnits(maxHarvesters);
        switch (priority) {
            case DEFENSE_PRIORITY: // Assign defense units first.
                assignDefenseUnits(maxDefenseWorkers, maxDefenseLights, maxDefenseRanged, maxDefenseHeavies);
                assignOffenseUnits(maxOffenseWorkers, maxOffenseLights, maxOffenseRanged, maxOffenseHeavies);
                break;
            case OFFENSE_PRIORITY: // Assign offense units first.
                assignOffenseUnits(maxOffenseWorkers, maxOffenseLights, maxOffenseRanged, maxOffenseHeavies);
                assignDefenseUnits(maxDefenseWorkers, maxDefenseLights, maxDefenseRanged, maxDefenseHeavies);
                break;
        }

    }

    /**
     * Assigns workers to the harvest group, under the maxHarvesters limit.
     * @param maxHarvesters Maximum allowed harvesters.
     */
    public void assignHarvestUnits(int maxHarvesters) {
        for (Unit worker : stateMonitor.getPlayerWorkers()) {
            if (maxHarvesters == -1 || harvestUnits.size() < maxHarvesters) {
                harvestUnits.add(worker);
            }
        }
    }

    /**
     * Assign defense units, under the specific limit of each unit type.
     * @param maxDefenseWorkers Maximum defense worker units.
     * @param maxDefenseLights Maximum defense light units.
     * @param maxDefenseRanged Maximum defense ranged units.
     * @param maxDefenseHeavies Maximum defense heavy units.
     */
    public void assignDefenseUnits(int maxDefenseWorkers, int maxDefenseLights, int maxDefenseRanged, int maxDefenseHeavies) {

        assignMaxUnitsToDefense(stateMonitor.getPlayerWorkers(), defenseWorkers, maxDefenseWorkers);
        assignMaxUnitsToDefense(stateMonitor.getPlayerLights(), defenseLights, maxDefenseLights);
        assignMaxUnitsToDefense(stateMonitor.getPlayerRanged(), defenseRanged, maxDefenseRanged);
        assignMaxUnitsToDefense(stateMonitor.getPlayerHeavies(), defenseHeavies, maxDefenseHeavies);

        defenseUnits.addAll(defenseWorkers);
        defenseUnits.addAll(defenseLights);
        defenseUnits.addAll(defenseRanged);
        defenseUnits.addAll(defenseHeavies);
    }

    /**
     * Assign offense units, under the specific limit of each unit type.
     * @param maxOffenseWorkers Maximum offense worker units.
     * @param maxOffenseLights Maximum offense light units.
     * @param maxOffenseRanged Maximum offense ranged units.
     * @param maxOffenseHeavies Maximum offense heavy units.
     */
    public void assignOffenseUnits(int maxOffenseWorkers, int maxOffenseLights, int maxOffenseRanged, int maxOffenseHeavies) {

        assignMaxUnitsToOffense(stateMonitor.getPlayerWorkers(), offenseWorkers, maxOffenseWorkers);
        assignMaxUnitsToOffense(stateMonitor.getPlayerLights(), offenseLights, maxOffenseLights);
        assignMaxUnitsToOffense(stateMonitor.getPlayerRanged(), offenseRanged, maxOffenseRanged);
        assignMaxUnitsToOffense(stateMonitor.getPlayerHeavies(), offenseHeavies, maxOffenseHeavies);

        offenseUnits.addAll(offenseWorkers);
        offenseUnits.addAll(offenseLights);
        offenseUnits.addAll(offenseRanged);
        offenseUnits.addAll(offenseHeavies);
    }

    /**
     * Assigns given units to the unit-type defense group, under the limits.
     * @param units The units to assign.
     * @param unitGroup The unit-type defense group.
     * @param maxUnits The unit number limit.
     */
    public void assignMaxUnitsToDefense(List<Unit> units, List<Unit> unitGroup, int maxUnits) {
        for (Unit unit : units)
            if ((maxUnits == -1 || unitGroup.size() < maxUnits) &&
                    // The unit should not belong to the offense or harvest group.
                    !offenseUnits.contains(unit) && !harvestUnits.contains(unit))
                unitGroup.add(unit);
    }

    /**
     * Assigns given units to the unit-type offense group, under the limits.
     * @param units The units to assign.
     * @param unitGroup The unit-type offense group.
     * @param maxUnits The unit number limit.
     */
    public void assignMaxUnitsToOffense(List<Unit> units, List<Unit> unitGroup, int maxUnits) {
        for (Unit unit : units)
            if ((maxUnits == -1 || unitGroup.size() < maxUnits) &&
                    // The unit should not belong to the defense or harvest group.
                    !defenseUnits.contains(unit) && !harvestUnits.contains(unit))
                unitGroup.add(unit);
    }

    /**
     * Switches stance from defense to defense, or the inverse.
     * @param unit The unit to switch stance.
     */
    public void fromDefenseToOffenseUnit(Unit unit) {

        // Switch to offense
        if (defenseUnits.contains(unit)) { // A defense unit.
            // Switch type sub group.
            /*switch (unit.getType().name) {
                case "Worker":
                    defenseWorkers.remove(unit);
                    offenseWorkers.add(unit);
                    break;
                case "Light":
                    defenseLights.remove(unit);
                    offenseLights.add(unit);
                    break;
                case "Ranged":
                    defenseRanged.remove(unit);
                    offenseRanged.add(unit);
                    break;
                case "Heavy":
                    defenseHeavies.remove(unit);
                    offenseHeavies.add(unit);
                    break;
            }*/
            // Switch group
            defenseUnits.remove(unit);
            offenseUnits.add(unit);
        }

    }

    public void fromOffenseToDefenseUnit(Unit unit) {
        // Switch to defense.
        if (offenseUnits.contains(unit)) {

            /*switch (unit.getType().name) {
                case "Worker":
                    offenseWorkers.remove(unit);
                    defenseWorkers.add(unit);
                    break;
                case "Light":
                    offenseLights.remove(unit);
                    defenseLights.add(unit);
                    break;
                case "Ranged":
                    offenseRanged.remove(unit);
                    defenseRanged.add(unit);
                    break;
                case "Heavy":
                    offenseHeavies.remove(unit);
                    defenseHeavies.add(unit);
                    break;
            }*/

            offenseUnits.remove(unit);
            defenseUnits.add(unit);
        }
    }

    /**
     * Switch all defense units to offense.
     */
    public void fromDefenseToOffenseAll() {
        while (defenseUnits.size() > 0)
            fromDefenseToOffenseUnit(defenseUnits.get(0));
    }

    public void fromDefenseToOffense(int count) {

        if (count < 0) {
            fromDefenseToOffenseAll();
            return;
        }

        while (count > 0 && !defenseUnits.isEmpty()) {
            fromDefenseToOffenseUnit(defenseUnits.get(0));
            count--;
        }
    }

    public List<Unit> getHarvestUnits() {
        return harvestUnits;
    }

    public List<Unit> getOffenseUnits() {
        return offenseUnits;
    }

    public List<Unit> getDefenseUnits() {
        return defenseUnits;
    }

    public List<Unit> getDefenseWorkers() {
        return defenseWorkers;
    }

    public List<Unit> getDefenseLights() {
        return defenseLights;
    }

    public List<Unit> getDefenseRanged() {
        return defenseRanged;
    }

    public List<Unit> getDefenseHeavies() {
        return defenseHeavies;
    }

    public List<Unit> getOffenseWorkers() {
        return offenseWorkers;
    }

    public List<Unit> getOffenseLights() {
        return offenseLights;
    }

    public List<Unit> getOffenseRanged() {
        return offenseRanged;
    }

    public List<Unit> getOffenseHeavies() {
        return offenseHeavies;
    }
}

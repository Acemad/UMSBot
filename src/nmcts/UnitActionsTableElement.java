package nmcts;

import rts.UnitAction;
import rts.units.Unit;

import java.util.List;

public class UnitActionsTableElement {
    public Unit unit;
    public int actionCount = 0;
    public List<UnitAction> actions;
    public double[] accumulatedEvaluation;
    public int[] visitsCount;
}

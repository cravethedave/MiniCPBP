package minicpbp.engine.constraints;

import minicpbp.engine.core.AbstractConstraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.util.CFG;

public class IncreasingValue extends AbstractConstraint{
    private IntVar[] x;
    private CFG g;
    private int n;

    public IncreasingValue(IntVar[] x, CFG g) {
        super(x[0].getSolver(), x);        
        setName("IncreasingValue");
        this.x = x;
        n = x.length;
        this.g = g;
    }
}

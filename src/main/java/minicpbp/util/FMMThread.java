package minicpbp.util;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import java.util.Arrays;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.examples.FMMTest;
import minicpbp.search.DFSearch;
import minicpbp.search.SearchStatistics;

public class FMMThread extends Thread {
    private int limit;
    private FMM fmm;

    public FMMThread(FMM fmm, int limit) {
        this.fmm = fmm;
        this.limit = limit;
    }

    public void run() {
        FMMTest.solve(fmm, limit);
    }
}

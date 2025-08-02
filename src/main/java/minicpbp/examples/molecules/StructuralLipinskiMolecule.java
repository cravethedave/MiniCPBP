package minicpbp.examples.molecules;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.engine.core.Solver.PropaMode;
import minicpbp.search.DFSearch;
import minicpbp.search.LDSearch;
import minicpbp.search.SearchStatistics;
import minicpbp.util.CFG;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;

public class StructuralLipinskiMolecule {
    static String FILE_PATH = "data/moleculeCNF_v12.txt";
    static int WORD_LENGTH = 40;
    static int SOLUTION_COUNT = 1;
    static int TIME_LIMIT = 600; // time in seconds
    

    public static void main(String[] args) {
        if (args.length < 7) {
            System.out.println("Please give the method name to run as well as the targeted property ranges/values as shown in the README. There should be 7 arguments.");
            return;
        }

        generateMoleculesLipinski(
            args[0],
            Integer.valueOf(args[1]),
            Integer.valueOf(args[2]),
            Integer.valueOf(args[3]),
            Integer.valueOf(args[4]),
            Integer.valueOf(args[5]),
            Integer.valueOf(args[6])
        );
    }

    private static void printSolution(CFG g, IntVar[] w, IntVar[] tokenWeights) {
        String word = "";
        int sumWeight = 0;
        for (int i = 0; i < w.length; i++) {
            word += g.tokenDecoder.get(w[i].min());
            sumWeight += tokenWeights[i].min();
        }
        System.out.println("\"" + word + "\", estimated weight: " + String.valueOf(sumWeight) + ", estimated logP: " + String.valueOf(logPEstimate.min()));

    }

    private static void generateMoleculesLipinski(
        String method,
        int minWeight,
        int maxWeight,
        int minLogP,
        int maxLogP,
        int nCycles,
        int nBranches
    ) {
        try {
            //region Base initialization
            Solver cp = makeSolver(false);
            CFG g = new CFG(FILE_PATH);
            
            // Create and name the token variables
            IntVar[] w = makeIntVarArray(cp, WORD_LENGTH, 0, g.terminalCount()-1);
            for (int i = 0; i < WORD_LENGTH; i++) {
                w[i].setName("token_" + i);
            }

            // Create and name the array of weight variables
            int minAtomWeight = Collections.min(g.tokenWeight.values());
            int maxAtomWeight = Collections.max(g.tokenWeight.values());
            IntVar[] tokenWeights = makeIntVarArray(cp, WORD_LENGTH, minAtomWeight, maxAtomWeight);
            for (int i = 0; i < WORD_LENGTH; i++) {
                tokenWeights[i].setName("weight_" + i);
            }
            //endregion
            
            //region Constraints
            // Smiles Validity
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,6);
            GenConstraints.cycleParityConstraint(cp,w,g,1,6);
            // Constraint to reduce donor/acceptor error by removing branches at the end of a sequence
            GenConstraints.avoidBranchOnEnd(cp, w, g);

            // Molecular weight
            IntVar weightTarget = makeIntVar(cp, minWeight, maxWeight);
            weightTarget.setName("Weight target");
            GenConstraints.moleculeWeightConstraint(cp,w,tokenWeights,weightTarget,g);

            // H-Acceptors
            IntVar acceptorTarget = makeIntVar(cp, 0, 10);
            acceptorTarget.setName("Acceptor target");
            GenConstraints.limitAcceptors(cp, w, g, acceptorTarget);

            // H-Donors
            IntVar donorTarget = makeIntVar(cp, 0, 5);
            donorTarget.setName("Donor target");
            GenConstraints.limitDonors(cp, w, g, donorTarget);

            // LogP
            IntVar logPEstimate = GenConstraints.regularLingo(cp, w, g, "data/lingo_changed.txt", minLogP, maxLogP);
            logPEstimate.setName("LogP estimate");
            
            // Cycle count
            if (nCycles > -1) {
                GenConstraints.limitCycleConstraint(cp, w, g, nCycles);
            }

            // Branch count
            if (nBranches > -1) {
                GenConstraints.limitBranchConstraint(cp, w, g, nBranches);
            }

            //endregion

            //region Solving
            cp.setTraceSearchFlag(false);
            cp.setTraceBPFlag(false);
            SearchStatistics stats;
            switch (method) {
                case "cpbp":
                    cp.setMode(PropaMode.SBP);
                    LDSearch lds = makeLds(cp, maxMarginalStrength(w));
                    lds.onSolution(() -> {printSolution(g,w,tokenWeights);});
                    stats = lds.solve(
                        stat -> stat.numberOfSolutions() == SOLUTION_COUNT || 
                        stat.timeElapsed() >= TIME_LIMIT * 1000
                    );
                    break;
                case "cp":
                default:
                    cp.setMode(PropaMode.SP);
                    DFSearch dfs = makeDfs(cp, domWdegRandom(w));                    
                    dfs.onSolution(() -> {printSolution(g,w,tokenWeights);});
                    stats = dfs.solveRestarts(
                        stat -> stat.numberOfSolutions() == SOLUTION_COUNT || 
                        stat.timeElapsed() >= TIME_LIMIT * 1000
                    );
            }
            System.out.println(stats);
            //endregion
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}

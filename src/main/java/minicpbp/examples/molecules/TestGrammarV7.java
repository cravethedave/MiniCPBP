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

import java.util.Collections;

public class TestGrammarV7 {
    public static void main(String[] args) {
        generateMolecules(
            "data/moleculeCNF_v7.txt",
            Integer.valueOf(args[0]),
            args[1],
            Integer.valueOf(args[2]),
            Integer.valueOf(args[3]),
            Integer.valueOf(args[4]),
            Integer.valueOf(args[5])
        );
        // generateMolecules(
        //     "data/moleculeCNF_v7.txt",
        //     20,
        //     "maxMarginal",
        //     4750,
        //     5000,
        //     1,
        //     2
        // );
    }

    private static void generateMolecules(
        String filePath,
        int wordLength,
        String method,
        int minWeight,
        int maxWeight,
        int nCycles,
        int nBranches
    ) {
        try {
            //#region Base initialization
            Solver cp = makeSolver(false);
            CFG g = new CFG(filePath);
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
            for (int i = 0; i < wordLength; i++) {
                w[i].setName("token_" + i);
            }
            
            IntVar weightTarget = makeIntVar(cp, minWeight, maxWeight);
            weightTarget.setName("Weight target");

            int minAtomWeight = Collections.min(g.tokenWeight.values());
            int maxAtomWeight = Collections.max(g.tokenWeight.values());
            IntVar[] tokenWeights = makeIntVarArray(cp, wordLength, minAtomWeight, maxAtomWeight);
            for (int i = 0; i < wordLength; i++) {
                tokenWeights[i].setName("weight_" + i);
            }
            //#endregion
            
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,8);
            GenConstraints.cycleParityConstraint(cp,w,g,1,8);
            GenConstraints.moleculeWeightConstraint(cp,w,tokenWeights,weightTarget,g);
            GenConstraints.limitCycleConstraint(cp, w, g, nCycles);
            GenConstraints.limitBranchConstraint(cp, w, g, nBranches);
            IntVar logPEstimate = GenConstraints.lingoConstraint(cp, w, g, "data/lingo_weights.txt", -50000, 50000);

            //#region Solve
            // Sampling, replace w by branching vars if wanted
            // double fraction = 0.005;
            // IntVar[] branchingVars = cp.sample(fraction,w);

            // cp.post(isEqual(w[1], g.tokenEncoder.get("1")));
            // cp.post(isEqual(w[wordLength - 1], g.tokenEncoder.get("1")));
            // cp.post(among(w, g.tokenEncoder.get("["), makeIntVar(cp, 0, 0)));
            // cp.post(among(w, g.tokenEncoder.get("="), makeIntVar(cp, 0, 0)));
            // cp.post(among(w, g.tokenEncoder.get("C"), makeIntVar(cp, wordLength-3, wordLength)));

            cp.setTraceSearchFlag(false);
            
            if (method.equals("maxMarginalLDS")) {
                cp.setMode(PropaMode.SBP);
                LDSearch lds = makeLds(cp, maxMarginal(w));
                lds.onSolution(() -> {
                    String word = "";
                    int sumWeight = 0;
                    for (int i = 0; i < wordLength; i++) {
                        word += g.tokenDecoder.get(w[i].min());
                        sumWeight += tokenWeights[i].min();
                    }
                    System.out.println(word + " weight of " + sumWeight);
                });
                System.out.println("[INFO] Now solving");
                System.out.println(lds.solve(stat -> stat.numberOfSolutions() == 1));
                return;
            } else if (method.equals("maxMarginalStrengthLDS")) {
                cp.setMode(PropaMode.SBP);
                LDSearch lds = makeLds(cp, maxMarginalStrength(w));
                lds.onSolution(() -> {
                    String word = "";
                    int sumWeight = 0;
                    for (int i = 0; i < wordLength; i++) {
                        word += g.tokenDecoder.get(w[i].min());
                        sumWeight += tokenWeights[i].min();
                    }
                    System.out.println(word + " weight of " + sumWeight);
                });
                System.out.println("[INFO] Now solving");
                System.out.println(lds.solve(stat -> stat.numberOfSolutions() == 1));
                return;
            } else if (method.equals("domWdegLDS")) {
                cp.setMode(PropaMode.SP);
                LDSearch lds = makeLds(cp, domWdeg(w));
                lds.onSolution(() -> {
                    String word = "";
                    int sumWeight = 0;
                    for (int i = 0; i < wordLength; i++) {
                        word += g.tokenDecoder.get(w[i].min());
                        sumWeight += tokenWeights[i].min();
                    }
                    System.out.println(word + " weight of " + sumWeight);
                });
                System.out.println("[INFO] Now solving");
                System.out.println(lds.solve(stat -> stat.numberOfSolutions() == 1));
                return;
            } else if (method.equals("impactLDS")) {
                cp.setMode(PropaMode.SP);
                LDSearch lds = makeLds(cp, impactBasedSearch(w));
                lds.onSolution(() -> {
                    String word = "";
                    int sumWeight = 0;
                    for (int i = 0; i < wordLength; i++) {
                        word += g.tokenDecoder.get(w[i].min());
                        sumWeight += tokenWeights[i].min();
                    }
                    System.out.println(word + " weight of " + sumWeight);
                });
                System.out.println("[INFO] Now solving");
                System.out.println(lds.solve(stat -> stat.numberOfSolutions() == 1));
                return;
            } else if (method.equals("minEntropyLDS")) {
                cp.setMode(PropaMode.SBP);
                LDSearch lds = makeLds(cp, minEntropy(w));
                lds.onSolution(() -> {
                    String word = "";
                    int sumWeight = 0;
                    for (int i = 0; i < wordLength; i++) {
                        word += g.tokenDecoder.get(w[i].min());
                        sumWeight += tokenWeights[i].min();
                    }
                    System.out.println(word + " weight of " + sumWeight);
                });
                System.out.println("[INFO] Now solving");
                System.out.println(lds.solve(stat -> stat.numberOfSolutions() == 1));
                return;
            }

            DFSearch dfs;
            switch (method) {
                case "minEntropy":
                    cp.setMode(PropaMode.SBP);
                    dfs = makeDfs(cp, minEntropy(w));
                    break;
                case "lexicoMarginal":
                    cp.setMode(PropaMode.SBP);
                    dfs = makeDfs(cp, lexicoMaxMarginalValue(w));
                    break;
                case "minEntropyBiasedWheel":
                    cp.setMode(PropaMode.SBP);
                    dfs = makeDfs(cp, minEntropyBiasedWheelSelectVal(w));
                    break;
                case "maxMarginalRestart":
                    cp.setMode(PropaMode.SBP);
                    dfs = makeDfs(cp, maxMarginalRandomTieBreak(w));
                    break;
                case "maxMarginalStrength":
                    cp.setMode(PropaMode.SBP);
                    dfs = makeDfs(cp, maxMarginalStrength(w));
                    break;
                case "maxMarginal":
                    cp.setMode(PropaMode.SBP);
                    dfs = makeDfs(cp, maxMarginal(w));
                    break;
                case "rnd":
                    cp.setMode(PropaMode.SP);
                    dfs = makeDfs(cp, randomVarRandomVal(w));
                    break;
                case "impactMinVal":
                case "impactMinValRestart":
                    cp.setMode(PropaMode.SBP);
                    dfs = makeDfs(cp, impactMinVal(w));
                    break;
                case "domWdegRandom":
                    cp.setMode(PropaMode.SBP);
                    dfs = makeDfs(cp, domWdegRandom(w));
                    break;
                case "domRaw":
                    cp.setMode(PropaMode.SBP);
                    dfs = makeDfs(cp, domRaw(w));
                    break;
                case "dom-random":
                    cp.setMode(PropaMode.SP);
                    dfs = makeDfs(cp, firstFailRandomVal(w));
                    break;
                case "impact":
                case "impactRestart":
                    cp.setMode(PropaMode.SBP);
                    dfs = makeDfs(cp, impactBasedSearch(w));
                    break;
                case "domWdegRestart":
                default:
                    cp.setMode(PropaMode.SP);
                    dfs = makeDfs(cp, domWdeg(w));
            }

            dfs.onSolution(() -> {
                String word = "";
                int sumWeight = 0;
                for (int i = 0; i < wordLength; i++) {
                    word += g.tokenDecoder.get(w[i].min());
                    sumWeight += tokenWeights[i].min();
                }
                System.out.println(word + " weight of " + sumWeight + " logP value of " + logPEstimate.min());
            });

            System.out.println("[INFO] Now solving");

            SearchStatistics stats;
            switch (method) {
                case "minEntropyBiasedWheel":
                case "maxMarginalRestart":
                case "impactRestart":
                case "domWdegRestart":
                case "impactMinValRestart":
                    System.out.println("Restarts");
                    stats = dfs.solveRestarts(stat -> stat.numberOfSolutions() == 1);
                    break;
                default:
                    System.out.println("No restarts");
                    stats = dfs.solve(stat -> stat.numberOfSolutions() == 1);
            }
            System.out.println(stats);
            //#endregion

            // This shows the marginals for each token
            // cp.fixPoint();
            // cp.vanillaBP(1);
            // int counter = 1;
            // for (IntVar iter : w) {
            //     System.out.println(String.format("Position %d", counter));
            //     for (int i = iter.min(); i <= iter.max(); i++) {
            //         if (iter.contains(i)) {
            //             System.out.println(String.format("%s %f", g.tokenDecoder.get(i), iter.marginal(i)));
            //         }
            //     }
            //     counter++;
            // }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void bp(String filePath) {
        CFG g;
        // InputReader reader = new InputReader("data/simpleCFG2.txt");
        // CFG g2 = new CFG(reader);
        try {
            g = new CFG(filePath);
        } catch (Exception e) {
            return;
        }
        int wordLength = 4;

        Solver cp = makeSolver();
        IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
        for (int i = 0; i < wordLength; i++)
            w[i].setName("w["+i+"]");

        cp.post(grammar(w,g));

        cp.fixPoint();
        cp.setTraceBPFlag(true);
        cp.vanillaBP(3);
    }
}

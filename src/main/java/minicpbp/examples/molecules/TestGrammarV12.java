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

public class TestGrammarV12 {
    public static void main(String[] args) {
        // GenConstraints.goingRndTest();
        // generateMoleculesSyntax(
        //     "data/moleculeCNF_v12.txt",
        //     Integer.valueOf(args[0]),
        //     args[1],
        //     Boolean.valueOf(args[2]),
        //     Boolean.valueOf(args[3]),
        //     Integer.valueOf(args[4]),
        //     Integer.valueOf(args[5]),
        //     Integer.valueOf(args[6]),
        //     Integer.valueOf(args[7]),
        //     Integer.valueOf(args[8])
        // );

        // generateMoleculesLipinski(
        //     "data/moleculeCNF_v12.txt",
        //     Integer.valueOf(args[0]),
        //     args[1],
        //     Boolean.valueOf(args[2]),
        //     Boolean.valueOf(args[3]),
        //     Integer.valueOf(args[4]),
        //     Integer.valueOf(args[5]),
        //     Integer.valueOf(args[6]),
        //     Integer.valueOf(args[7]),
        //     Integer.valueOf(args[8]),
        //     Integer.valueOf(args[9]),
        //     Integer.valueOf(args[10])
        // );

        // C1(=O)CN=C(c2ccccc2)c3cc(_)ccc3N1___
        // "C1(=O)CN=C(c2ccccc2)*3*c******3**"
        // CCC1(C(=O)TC(=O)TC1=O)c2ccccc2
        // O=C1TC(=O)TC(=O)C1(c2ccccc2)CC
        // completeMolecule(
        //     "data/moleculeCNF_v1.txt",
        //     Integer.valueOf(args[0]),
        //     "O=C1NC(=O)NC(=O)C1(c2ccccc2)CC",
        //     Boolean.valueOf(args[1]),
        //     Integer.valueOf(args[2]),
        //     Integer.valueOf(args[3]),
        //     Integer.valueOf(args[4]),
        //     Integer.valueOf(args[5]),
        //     Integer.valueOf(args[6]),
        //     Integer.valueOf(args[7])
        // );

        customGeneration(
            "data/moleculeCNF_v12.txt",
            40,
            "maxMarginalStrengthLDS",
            false,
            50,
            600
        );
    }

    private static void updateSolver(Solver cp) {
        cp.fixPoint();
        // cp.beliefPropa();
        cp.vanillaBP(4);
    }

    private static void customGeneration(
        String filePath,
        int wordLength,
        String method,
        boolean doLipinski,
        int numSolutions,
        int limitInSeconds
    ) {
        long startTime = System.currentTimeMillis()/1000;
        try {
            //#region Base initialization
            Solver cp = makeSolver(false);
            CFG g = new CFG(filePath);
            // g.printTokens();
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
            for (int i = 0; i < wordLength; i++) {
                w[i].setName("token_" + i);
            }

            int minAtomWeight = Collections.min(g.tokenWeight.values());
            int maxAtomWeight = Collections.max(g.tokenWeight.values());
            IntVar[] tokenWeights = makeIntVarArray(cp, wordLength, minAtomWeight, maxAtomWeight);
            for (int i = 0; i < wordLength; i++) {
                tokenWeights[i].setName("weight_" + i);
            }
            //#endregion
            
            // Smiles Validity
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,6);
            GenConstraints.cycleParityConstraint(cp,w,g,1,6);
            // Other constraints
            IntVar logPEstimate = makeIntVar(cp, 0, 0);
            logPEstimate.setName("LogP estimate");
            
            // GenConstraints.limitCycleConstraint(cp, w, g, 3);
            // cp.post(among(w, g.tokenEncoder.get("c"), makeIntVar(cp, 12, 12)));

            String fileName = "custom_molecule.txt";
            cp.setTraceSearchFlag(false);
            cp.setTraceBPFlag(false);
            switch (method) {
                case "maxMarginalStrengthLDS":
                case "domWdegLDS":
                case "impactLDS":
                case "minEntropyLDS":
                case "maxMarginalLDS":
                    solveLDS(cp, w, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
                    break;
                default:
                    solveDFS(cp, w, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void generateMoleculesSyntax(
        String filePath,
        int wordLength,
        String method,
        boolean doLipinski,
        boolean doSampling,
        int k,
        int numSolutions,
        int limitInSeconds,
        int numCycles,
        int numBranches
    ) {
        long startTime = System.currentTimeMillis()/1000;
        try {
            //#region Base initialization
            Solver cp = makeSolver(false);
            CFG g = new CFG(filePath);
            // g.printTokens();
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
            for (int i = 0; i < wordLength; i++) {
                w[i].setName("token_" + i);
            }

            int minAtomWeight = Collections.min(g.tokenWeight.values());
            int maxAtomWeight = Collections.max(g.tokenWeight.values());
            IntVar[] tokenWeights = makeIntVarArray(cp, wordLength, minAtomWeight, maxAtomWeight);
            for (int i = 0; i < wordLength; i++) {
                tokenWeights[i].setName("weight_" + i);
            }
            //#endregion
            
            // Smiles Validity
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,6);
            GenConstraints.cycleParityConstraint(cp,w,g,1,6);
            // Other constraints
            IntVar logPEstimate = makeIntVar(cp, 0, 0);
            logPEstimate.setName("LogP estimate");
            if (doLipinski) {
                // Fix to the grammar to reduce donor/acceptor error
                // GenConstraints.avoidBranchOnEnd(cp, w, g);
                // Molecular weight
                IntVar weightTarget = makeIntVar(cp, 4750, 5000);
                weightTarget.setName("Weight target");
                GenConstraints.moleculeWeightConstraint(cp,w,tokenWeights,weightTarget,g);
                // H Donors
                IntVar donorTarget = makeIntVar(cp, 0, 5);
                donorTarget.setName("Donor target");
                // GenConstraints.limitDonors(cp, w, g, donorTarget);
                // H Acceptors
                IntVar acceptorTarget = makeIntVar(cp, 0, 10);
                acceptorTarget.setName("Acceptor target");
                // GenConstraints.limitAcceptors(cp, w, g, acceptorTarget);
                // LogP
                // logPEstimate = GenConstraints.lingoConstraint(cp, w, g, "data/lingo_changed.txt", 200, 500);
            }
            if (numBranches != 0) {
                GenConstraints.limitBranchConstraint(cp, w, g, numBranches);
            }
            if (numCycles != 0) {
                GenConstraints.limitCycleConstraint(cp, w, g, numCycles);
            }
   
            String fileName = "results_" + method + "_sz" + wordLength;
            if (numCycles != 0) {
                fileName += "_cycle" + numCycles;
            }
            if (numBranches != 0) {
                fileName += "_brnch" + numBranches;
            }
            if (doLipinski) {
                fileName += "_lip";
            }
            if (doSampling) {
                fileName += "_smpl" + k;
            }
            if (numSolutions != 0) {
                fileName += "_" + numSolutions + "sols";
            }
            if (limitInSeconds != 0) {
                fileName += "_" + limitInSeconds + "secs";
            }
            fileName += ".txt";
            cp.setTraceSearchFlag(false);
            cp.setTraceBPFlag(false);
            switch (method) {
                case "maxMarginalStrengthLDS":
                case "domWdegLDS":
                case "impactLDS":
                case "minEntropyLDS":
                case "maxMarginalLDS":
                    if (doSampling) {
                        double fraction = Math.pow(37, -k);
                        IntVar[] branchingVars = cp.sample(fraction,w);
                        solveLDS(cp, branchingVars, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
                    } else {
                        solveLDS(cp, w, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
                    }
                    break;
                default:
                    if (doSampling) {
                        double fraction = Math.pow(37, -k);
                        IntVar[] branchingVars = cp.sample(fraction,w);
                        solveDFS(cp, branchingVars, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
                    } else {
                        solveDFS(cp, w, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
                    }
            }

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

    private static void generateMoleculesLipinski(
        String filePath,
        int wordLength,
        String method,
        boolean doLipinski,
        boolean doSampling,
        int k,
        int numSolutions,
        int limitInSeconds,
        int minWeight,
        int maxWeight,
        int minLogP,
        int maxLogP
    ) {
        long startTime = System.currentTimeMillis()/1000;
        try {
            //#region Base initialization
            Solver cp = makeSolver(false);
            CFG g = new CFG(filePath);
            System.out.println(g.nonTerminalCount());
            // g.printTokens();
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
            for (int i = 0; i < wordLength; i++) {
                w[i].setName("token_" + i);
            }

            int minAtomWeight = Collections.min(g.tokenWeight.values());
            int maxAtomWeight = Collections.max(g.tokenWeight.values());
            IntVar[] tokenWeights = makeIntVarArray(cp, wordLength, minAtomWeight, maxAtomWeight);
            for (int i = 0; i < wordLength; i++) {
                tokenWeights[i].setName("weight_" + i);
            }
            //#endregion
            
            // Smiles Validity
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,6);
            GenConstraints.cycleParityConstraint(cp,w,g,1,6);
            // Other constraints
            IntVar logPEstimate = makeIntVar(cp, 0, 0);
            logPEstimate.setName("LogP estimate");
            if (doLipinski) {
                // Fix to the grammar to reduce donor/acceptor error
                GenConstraints.avoidBranchOnEnd(cp, w, g);
                // Molecular weight
                IntVar weightTarget = makeIntVar(cp, minWeight, maxWeight);
                weightTarget.setName("Weight target");
                GenConstraints.moleculeWeightConstraint(cp,w,tokenWeights,weightTarget,g);
                // H Donors
                IntVar donorTarget = makeIntVar(cp, 0, 5);
                donorTarget.setName("Donor target");
                GenConstraints.limitDonors(cp, w, g, donorTarget);
                // H Acceptors
                IntVar acceptorTarget = makeIntVar(cp, 0, 10);
                acceptorTarget.setName("Acceptor target");
                GenConstraints.limitAcceptors(cp, w, g, acceptorTarget);
                // LogP
                logPEstimate = GenConstraints.lingoConstraint(cp, w, g, "data/lingo_changed.txt", minLogP, maxLogP);
            }
   
            String fileName = "results_" + method + "_sz" + wordLength;
            if (doLipinski) {
                fileName += "_lip";
            }
            if (doSampling) {
                fileName += "_smpl" + k;
            }
            if (numSolutions != 0) {
                fileName += "_" + numSolutions + "sols";
            }
            if (limitInSeconds != 0) {
                fileName += "_" + limitInSeconds + "secs";
            }
            fileName += "_" + String.valueOf(minWeight) + "-" + String.valueOf(maxWeight) + "_" + String.valueOf(minLogP) + "-" + String.valueOf(maxLogP);
            fileName += ".txt";
            cp.setTraceSearchFlag(false);
            cp.setTraceBPFlag(false);
            switch (method) {
                case "maxMarginalStrengthLDS":
                case "domWdegLDS":
                case "impactLDS":
                case "minEntropyLDS":
                case "maxMarginalLDS":
                    if (doSampling) {
                        double fraction = Math.pow(37, -k);
                        IntVar[] branchingVars = cp.sample(fraction,w);
                        solveLDS(cp, branchingVars, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
                    } else {
                        solveLDS(cp, w, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
                    }
                    break;
                default:
                    if (doSampling) {
                        double fraction = Math.pow(37, -k);
                        IntVar[] branchingVars = cp.sample(fraction,w);
                        solveDFS(cp, branchingVars, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
                    } else {
                        solveDFS(cp, w, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
                    }
            }

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

    private static void completeMolecule(
        String filePath,
        int wordLength,
        String moleculeString,
        boolean doLipinski,
        int numSolutions,
        int limitInSeconds,
        int minWeight,
        int maxWeight,
        int minLogP,
        int maxLogP
    ) {
        try {
            //#region Base initialization
            Solver cp = makeSolver(false);
            CFG g = new CFG(filePath);
            // g.printTokens();
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
            for (int i = 0; i < wordLength; i++) {
                w[i].setName("token_" + i);
            }

            int minAtomWeight = Collections.min(g.tokenWeight.values());
            int maxAtomWeight = Collections.max(g.tokenWeight.values());
            IntVar[] tokenWeights = makeIntVarArray(cp, wordLength, minAtomWeight, maxAtomWeight);
            for (int i = 0; i < wordLength; i++) {
                tokenWeights[i].setName("weight_" + i);
            }
            //#endregion
            
            // Smiles Validity
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,6);
            GenConstraints.cycleParityConstraint(cp,w,g,1,6);
            // Other constraints
            IntVar logPEstimate = makeIntVar(cp, 0, 0);
            logPEstimate.setName("LogP estimate");
            if (doLipinski) {
                // Fix to the grammar to reduce donor/acceptor error
                // GenConstraints.avoidBranchOnEnd(cp, w, g);
                // Molecular weight
                IntVar weightTarget = makeIntVar(cp, minWeight, maxWeight);
                weightTarget.setName("Weight target");
                // GenConstraints.moleculeWeightConstraint(cp,w,tokenWeights,weightTarget,g);
                // H Donors
                IntVar donorTarget = makeIntVar(cp, 0, 5);
                donorTarget.setName("Donor target");
                // GenConstraints.limitDonors(cp, w, g, donorTarget);
                // H Acceptors
                IntVar acceptorTarget = makeIntVar(cp, 0, 10);
                acceptorTarget.setName("Acceptor target");
                // GenConstraints.limitAcceptors(cp, w, g, acceptorTarget);
                // LogP
                // logPEstimate = GenConstraints.lingoConstraint(cp, w, g, "data/lingo_changed.txt", minLogP, maxLogP);
            }

            String[] tokenizedMolecule = new String[0];
            tokenizedMolecule = Tokenizers.tokenizeV7(moleculeString).toArray(tokenizedMolecule);
            for (int i = 0; i < tokenizedMolecule.length; i++) {
                if (tokenizedMolecule[i].equals("*")) {
                    continue;
                }
                System.out.println(tokenizedMolecule[i]);
                System.out.println(g.tokenEncoder.get(tokenizedMolecule[i]));
                System.out.println(w[i].toString());
                System.out.println();
                w[i].assign(g.tokenEncoder.get(tokenizedMolecule[i]));
                updateSolver(cp);
            }

            System.out.println("Molecule recognized");
   
            String fileName = "results_sz" + wordLength;
            if (doLipinski) {
                fileName += "_lip";
            }
            if (numSolutions != 0) {
                fileName += "_" + numSolutions + "sols";
            }
            if (limitInSeconds != 0) {
                fileName += "_" + limitInSeconds + "secs";
            }
            fileName += "_" + String.valueOf(minWeight) + "-" + String.valueOf(maxWeight) + "_" + String.valueOf(minLogP) + "-" + String.valueOf(maxLogP);
            fileName += ".txt";
            cp.setTraceSearchFlag(false);
            cp.setTraceBPFlag(false);

            String method = "maxMarginalStrengthLDS";
            switch (method) {
                case "maxMarginalStrengthLDS":
                case "domWdegLDS":
                case "impactLDS":
                case "minEntropyLDS":
                case "maxMarginalLDS":
                    solveLDS(cp, w, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
                    break;
                default:
                    solveDFS(cp, w, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void solveLDS(
        Solver cp,
        IntVar[] targetArray,
        CFG g,
        String method,
        IntVar[] tokenWeights,
        IntVar logPEstimate,
        int numSolutions,
        int limitInSeconds,
        String fileName
    ) {
        // try {
        //     FileWriter resultsWriter = new FileWriter(fileName, false);
        //     resultsWriter.write("");
        //     resultsWriter.close();
        // } catch (IOException e) {
        //     System.out.println("[ERROR] File not writing ********************");
        // }

        LDSearch lds;
        switch (method) {
            case "maxMarginalStrengthLDS":
                cp.setMode(PropaMode.SBP);
                lds = makeLds(cp, maxMarginalStrength(targetArray));
                break;
            case "domWdegLDS":
                cp.setMode(PropaMode.SP);
                lds = makeLds(cp, domWdeg(targetArray));
                break;
            case "impactLDS":
                cp.setMode(PropaMode.SP);
                lds = makeLds(cp, impactBasedSearch(targetArray));
                break;
            case "minEntropyLDS":
                cp.setMode(PropaMode.SBP);
                lds = makeLds(cp, minEntropy(targetArray));
                break;
            case "maxMarginalLDS":
            default:
                cp.setMode(PropaMode.SBP);
                lds = makeLds(cp, maxMarginal(targetArray));
        }
        
        lds.onSolution(() -> {
            String word = "";
            int sumWeight = 0;
            for (int i = 0; i < targetArray.length; i++) {
                word += g.tokenDecoder.get(targetArray[i].min());
                sumWeight += tokenWeights[i].min();
            }
            // System.out.println(word + " weight of " + sumWeight + " logP of " + logPEstimate.min());
            System.out.println("\"" + word + "\",");
            try {
                FileWriter resultsWriter = new FileWriter(fileName, true);
                resultsWriter.write(
                    word + "," +
                    String.valueOf(sumWeight) + "," +
                    String.valueOf(logPEstimate.min()) + "," +
                    "\n"
                );
                resultsWriter.close();
            } catch (IOException e) {
                System.out.println("[ERROR] File not writing ********************");
            }
        });

        System.out.println("[INFO] Now solving");
        SearchStatistics stats;
        if (numSolutions == 0 && limitInSeconds == 0) {
            stats = lds.solve();
        } else if (limitInSeconds == 0) {
            stats = lds.solve(stat -> stat.numberOfSolutions() == numSolutions);
        } else if (numSolutions == 0) {
            stats = lds.solve(stat -> stat.timeElapsed() >= limitInSeconds * 1000);
        } else {
            stats = lds.solve(stat -> stat.numberOfSolutions() == numSolutions || stat.timeElapsed() >= limitInSeconds * 1000);
        }

        try {
            FileWriter resultsWriter = new FileWriter(fileName, true);
            resultsWriter.write("\n\n");
            resultsWriter.write(stats.toString());
            resultsWriter.close();
        } catch (IOException e) {
            System.out.println("[ERROR] File not writing ********************");
        }
        System.out.println(stats);
    }

    private static void solveDFS(
        Solver cp,
        IntVar[] targetArray,
        CFG g,
        String method,
        IntVar[] tokenWeights,
        IntVar logPEstimate,
        int numSolutions,
        int limitInSeconds,
        String fileName
    ) {
        // try {
        //     FileWriter resultsWriter = new FileWriter(fileName, false);
        //     resultsWriter.write("");
        //     resultsWriter.close();
        // } catch (IOException e) {
        //     System.out.println("[ERROR] File not writing ********************");
        // }

        DFSearch dfs;
        switch (method) {
            case "maxMarginalStrengthBiasedWheelSelectVal":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, maxMarginalStrengthBiasedWheelSelectVal(targetArray));
                break;
            case "domWdegMaxMarginalValue":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, domWdegMaxMarginalValue(targetArray));
                break;
            case "firstFailMaxMarginalValue":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, firstFailMaxMarginalValue(targetArray));
                break;
            case "minEntropy":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, minEntropy(targetArray));
                break;
            case "lexicoMarginal":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, lexicoMaxMarginalValue(targetArray));
                break;
            case "minEntropyBiasedWheel":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, minEntropyBiasedWheelSelectVal(targetArray));
                break;
            case "maxMarginalRestart":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, maxMarginalRandomTieBreak(targetArray));
                break;
            case "maxMarginalStrength":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, maxMarginalStrength(targetArray));
                break;
            case "maxMarginal":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, maxMarginal(targetArray));
                break;
            case "rnd":
                cp.setMode(PropaMode.SP);
                dfs = makeDfs(cp, randomVarRandomVal(targetArray));
                break;
            case "impactMinVal":
            case "impactMinValRestart":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, impactMinVal(targetArray));
                break;
            case "domWdegRandom":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, domWdegRandom(targetArray));
                break;
            case "domRaw":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, domRaw(targetArray));
                break;
            case "dom-random":
                cp.setMode(PropaMode.SP);
                dfs = makeDfs(cp, firstFailRandomVal(targetArray));
                break;
            case "impact":
            case "impactRestart":
                cp.setMode(PropaMode.SBP);
                dfs = makeDfs(cp, impactBasedSearch(targetArray));
                break;
            case "domWdeg":
            default:
                cp.setMode(PropaMode.SP);
                dfs = makeDfs(cp, domWdeg(targetArray));
        }

        dfs.onSolution(() -> {
            String word = "";
            int sumWeight = 0;
            for (int i = 0; i < targetArray.length; i++) {
                word += g.tokenDecoder.get(targetArray[i].min());
                sumWeight += tokenWeights[i].min();
            }
            // System.out.println(word + " weight of " + sumWeight + " logP of " + logPEstimate.min());
            System.out.println("\"" + word + "\",");
            try {
                FileWriter resultsWriter = new FileWriter(fileName, true);
                resultsWriter.write(
                    word + "," +
                    String.valueOf(sumWeight) + "," +
                    String.valueOf(logPEstimate.min()) + "," +
                    "\n"
                );
                resultsWriter.close();
            } catch (IOException e) {
                System.out.println("[ERROR] File not writing ********************");
            }
        });

        System.out.println("[INFO] Now solving");

        SearchStatistics stats;
        switch (method) {
            // case "minEntropyBiasedWheel":
            case "maxMarginalRestart":
            case "impactRestart":
            case "domWdeg":
            case "domWdegRandom":
            case "domWdegMaxMarginalValue":
            case "impactMinValRestart":
                System.out.println("Restarts");
                if (numSolutions == 0 && limitInSeconds == 0) {
                    stats = dfs.solveRestarts();
                } else if (limitInSeconds == 0) {
                    stats = dfs.solveRestarts(stat -> stat.numberOfSolutions() == numSolutions);
                } else if (numSolutions == 0) {
                    stats = dfs.solveRestarts(stat -> stat.timeElapsed() >= limitInSeconds * 1000);
                } else {
                    stats = dfs.solveRestarts(stat -> stat.numberOfSolutions() == numSolutions || stat.timeElapsed() >= limitInSeconds * 1000);
                }
                break;
            default:
                System.out.println("No restarts");
                if (numSolutions == 0 && limitInSeconds == 0) {
                    stats = dfs.solve();
                } else if (limitInSeconds == 0) {
                    stats = dfs.solve(stat -> stat.numberOfSolutions() == numSolutions);
                } else if (numSolutions == 0) {
                    stats = dfs.solve(stat -> stat.timeElapsed() >= limitInSeconds * 1000);
                } else {
                    stats = dfs.solve(stat -> stat.numberOfSolutions() == numSolutions || stat.timeElapsed() >= limitInSeconds * 1000);
                }
        }

        try {
            FileWriter resultsWriter = new FileWriter(fileName, true);
            resultsWriter.write("\n\n");
            resultsWriter.write(stats.toString());
            resultsWriter.close();
        } catch (IOException e) {
            System.out.println("[ERROR] File not writing ********************");
        }
        System.out.println(stats);
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

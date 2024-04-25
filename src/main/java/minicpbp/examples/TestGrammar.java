package minicpbp.examples;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.engine.core.Solver.PropaMode;
import minicpbp.search.DFSearch;
import minicpbp.search.LDSearch;
import minicpbp.search.SearchStatistics;
import minicpbp.util.CFG;
import minicpbp.util.Procedure;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.Vector;

public class TestGrammar {
    public static void main(String[] args) {
        generateMolecules(
            "data/moleculeCNF_v6.txt",
            Integer.valueOf(args[0]),
            args[1],
            Integer.valueOf(args[2]),
            Integer.valueOf(args[3]),
            Integer.valueOf(args[4]),
            Integer.valueOf(args[5]),
            Integer.valueOf(args[6]),
            Integer.valueOf(args[7])
        );
        // testMoleculeValidity("data/moleculeCNF_v6.txt", args[0], 0);
        // bulkMolVal("data/moleculeCNF_v6.txt", "../py_master_utils/molecules/MOSES.txt");
        
        // try {
        //     CFG g = new CFG("data/moleculeCNF_v6.txt");
        //     System.out.println(g.terminalCount());
        //     System.out.println(g.nonTerminalCount());
        // } catch (Exception e) {}
    }

    private static void bulkMolVal(String grammarPath, String moleculePath) {
        Vector<Vector<String>> molecules = new Vector<>();
        try {
            File moleculeFile = new File(moleculePath);
            Scanner reader = new Scanner(moleculeFile);
            while (reader.hasNextLine()) {
                molecules.add(tokenize(reader.nextLine()));
            }
            reader.close();
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        }

        //#region Constraints setup
        // CFG
        CFG g = null;
        try {
            g = new CFG(grammarPath);
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        } catch (IOException e) {
            System.out.println("IOException");
        }

        // Cycle counting
        int startingNb = 1;
        int endNb = 19;
        int nbCount = endNb - startingNb + 1;
        int[] values = new int[nbCount];
        for (int i = startingNb; i < startingNb + nbCount; i++) {
            String key = i < 10 ? Integer.toString(i) : '%' + Integer.toString(i);
            values[i-startingNb] = g.tokenEncoder.get(key);
        }

        int n = g.terminalCount();
        int[][] A = new int[nbCount][n];
        for (int i = 0; i < nbCount; i++) {
            // Default loop
            for (int j = 0; j < n; j++) {
                A[i][j] = i;
            }
            // Disallow all numbers above the one associated to the current state
            for (int j = i + 1; j < values.length; j++) {
                A[i][values[j]] = -1;
            }
            // Create the transitions to go up a state, except for the last state
            if (i != A.length - 1) {
                A[i][values[i]] = i + 1;
            }
        }
        //#endregion

        System.out.println("[INFO] Done setup");

        int processed = 0;
        float failures = 0.0f;
        for (Vector<String> tokens : molecules) {
            if (processed % 1000 == 0) {
                System.out.println("[INFO] Failed " + failures + " of the " + processed + " processed");
            }
            processed++;
            int wordLength = tokens.size();
            Solver cp = makeSolver(false);
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
            for (int i = 0; i < wordLength; i++) {
                w[i].setName("w_" + i);
            }
            boolean valid = true;
            for (int i = 0; i < tokens.size(); i++) {
                if (!g.tokenEncoder.containsKey(tokens.get(i))) {
                    valid = false;
                    System.out.println("[WARN] Grammar does not contain " + tokens.get(i));
                    failures++;
                    break;
                }
                Integer token = g.tokenEncoder.get(tokens.get(i));
                w[i].assign(token);
            }

            if (!valid) continue;

            //#region Constraints setting
            try {
                cp.post(grammar(w,g));
            } catch (Exception e) {
                valid = false;
                failures++;
                // System.out.println("Failed during grammar constraint");
            }
            
            if (!valid) continue;

            try {
                cp.post(regular(w, A));
            } catch (Exception e) {
                valid = false;
                failures++;
                // System.out.println("Failed during regular constraint");
            }
            
            if (!valid) continue;
    
            for (int i = 0; i < values.length; i++) {
                IntVar numberOccurrences = makeIntVar(cp, 0, 2);
                numberOccurrences.remove(1);
                try {
                    cp.post(among(w, values[i], numberOccurrences));
                } catch (Exception e) {
                    valid = false;
                    failures++;
                    break;
                    // System.out.println("Failed during parity constraint");
                }
            }
            //#endregion
        }
        
        System.out.println("[INFO] Failed " + failures + " out of " + molecules.size() + ". " + failures/molecules.size() * 100 + "% success rate.");
        System.out.println(Math.round(failures));
    }

    private static Vector<String> tokenize(String molecule) {
        char[] moleculeChars = molecule.toCharArray();
        Vector<String> tokens = new Vector<>();
        for (int i = 0; i < moleculeChars.length; i++) {
            if (moleculeChars[i] == '%') {
                tokens.add(String.format("%%c%c", moleculeChars[i+1], moleculeChars[i+2]));
                i += 2; // Skips the next two chars
                continue;
            } else if (i != moleculeChars.length - 1 && moleculeChars[i] == 'C' && moleculeChars[i+1] == 'l') {
                tokens.add("Cl");
                i += 1;
                continue;
            } else if (i != moleculeChars.length - 1 && moleculeChars[i] == 'B' && moleculeChars[i+1] == 'r') {
                tokens.add("Br");
                i += 1;
                continue;
            } else if (i != moleculeChars.length - 1 && moleculeChars[i] == 'H' && moleculeChars[i+1] == '3') {
                tokens.add("H3");
                i += 1;
                continue;
            }
            tokens.add(String.format("%c", moleculeChars[i]));
        }
        return tokens;
    }

    private static void testMoleculeValidity(String filePath, String molecule, int decal) {
        try {
            CFG g = new CFG(filePath);

            char[] moleculeChars = molecule.toCharArray();
            Vector<String> tokens = new Vector<>();
            for (int i = 0; i < moleculeChars.length; i++) {
                if (moleculeChars[i] == '%') {
                    tokens.add(String.format("%%c%c", moleculeChars[i+1], moleculeChars[i+2]));
                    i += 2; // Skips the next two chars
                    continue;
                } else if (i != moleculeChars.length - 1 && moleculeChars[i] == 'C' && moleculeChars[i+1] == 'l') {
                    tokens.add("Cl");
                    i += 1;
                    continue;
                } else if (i != moleculeChars.length - 1 && moleculeChars[i] == 'B' && moleculeChars[i+1] == 'r') {
                    tokens.add("Br");
                    i += 1;
                    continue;
                } else if (i != moleculeChars.length - 1 && moleculeChars[i] == 'H' && moleculeChars[i+1] == '3') {
                    tokens.add("H3");
                    i += 1;
                    continue;
                }
                tokens.add(String.format("%c", moleculeChars[i]));
            }

            int wordLength = tokens.size();
            Solver cp = makeSolver(false);
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
            for (int i = 0; i < wordLength; i++) {
                w[i].setName("w_" + i);
            }
            for (int i = 0; i < tokens.size(); i++) {
                Integer token = g.tokenEncoder.get(tokens.get(i));
                w[i].assign(token);
            }

            //#region Grammar constraint
            try {
                cp.post(grammar(w,g));
            } catch (Exception e) {
                System.out.println("Failed during grammar constraint");
                throw e;
            }
            //#endregion

            //#region Cycle counting constraint
            int startingNb = 1;
            int endNb = 19;
            int nbCount = endNb - startingNb + 1;
            int[] values = new int[nbCount];
            for (int i = startingNb; i < startingNb + nbCount; i++) {
                String key = i < 10 ? Integer.toString(i) : '%' + Integer.toString(i);
                values[i-startingNb] = g.tokenEncoder.get(key);
            }

            int n = g.terminalCount();
            int[][] A = new int[nbCount][n];
            for (int i = 0; i < nbCount; i++) {
                // Default loop
                for (int j = 0; j < n; j++) {
                    A[i][j] = i;
                }
                // Disallow all numbers above the one associated to the current state
                for (int j = i + 1; j < values.length; j++) {
                    A[i][values[j]] = -1;
                }
                // Create the transitions to go up a state, except for the last state
                if (i != A.length - 1) {
                    A[i][values[i]] = i + 1;
                }
            }
            
            try {
                cp.post(regular(w, A));
            } catch (Exception e) {
                System.out.println("Failed during regular constraint");
                throw e;
            }
            //#endregion

            //#region Cycle parity constraint
            for (int i = 0; i < values.length; i++) {
                IntVar numberOccurrences = makeIntVar(cp, 0, 2);
                numberOccurrences.remove(1);
                try {
                    cp.post(among(w, values[i], numberOccurrences));
                } catch (Exception e) {
                    System.out.println("Failed during parity constraint");
                    throw e;
                }
            }
            //#endregion

            //#region Carbon percentage
            // int[] carbonAtoms = new int[2];
            // carbonAtoms[0] = g.tokenEncoder.get("C");
            // carbonAtoms[1] = g.tokenEncoder.get("c");
            // int lowerBound = Math.round(wordLength * 0.35f);
            // int upperBound = Math.round(wordLength * 0.65f);
            // IntVar carbonRange = makeIntVar(cp, lowerBound, upperBound);
            // try {
            //     cp.post(among(w,carbonAtoms,carbonRange));
            // } catch (Exception e) {
            //     System.out.println("Failed during carbon constraint");
            //     throw e;
            // }
            //#endregion

            //#region Molecular weight constraint
            // IntVar weightTarget = makeIntVar(cp, 2996, 3000);
            // weightTarget.setName("Weight target");
            // IntVar[] tokenWeights = makeIntVarArray(cp, wordLength, 0, 1259);
            // for (int i = 0; i < wordLength; i++) {
            //     tokenWeights[i].setName("weight_" + i);
            // }
            // int[] tokenToWeight = new int[g.terminalCount()];
            // for (int i = 0; i < g.terminalCount(); i++) {
            //     String token = g.tokenDecoder.get(i).toLowerCase();
            //     if (g.tokenWeight.containsKey(token)) {
            //         tokenToWeight[i] = g.tokenWeight.get(token);
            //     } else {
            //         tokenToWeight[i] = 0;
            //     }
            // }
            // for (int i = 0; i < wordLength; i++) {
            //     cp.post(element(tokenToWeight, w[i], tokenWeights[i]));
            // }
            // try {
            //     cp.post(sum(tokenWeights, weightTarget));
            // } catch (Exception e) {
            //     System.out.println("Failed during weight constraint");
            //     throw e;
            // }
            //#endregion

            System.out.println("[SUCCESS] " + molecule);
        } catch (Exception e) {
            System.out.println("[FAIL] " + molecule);
        }
    }

    private static void generateMolecules(
        String filePath,
        int wordLength,
        String method,
        int minWeight,
        int maxWeight,
        int minCarbon,
        int maxCarbon,
        int nCycles,
        int nBranches
    ) {
        try {
            //#region Base initialization
            Solver cp = makeSolver(false);
            //#endregion
            
            //#region Grammar constraint
            CFG g = new CFG(filePath);
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
            for (int i = 0; i < wordLength; i++) {
                w[i].setName("w_" + i);
            }
            cp.post(grammar(w,g));
            //#endregion

            //#region Cycle counting constraint
            int startingNb = 1;
            int endNb = 19;
            int nbCount = endNb - startingNb + 1;
            int[] values = new int[nbCount];
            for (int i = startingNb; i < startingNb + nbCount; i++) {
                String key = i < 10 ? Integer.toString(i) : '%' + Integer.toString(i);
                values[i-startingNb] = g.tokenEncoder.get(key);
            }

            int n = g.terminalCount();
            int[][] A = new int[nbCount][n];
            for (int i = 0; i < nbCount; i++) {
                // Default loop
                for (int j = 0; j < n; j++) {
                    A[i][j] = i;
                }
                // Disallow all numbers above the one associated to the current state
                for (int j = i + 1; j < values.length; j++) {
                    A[i][values[j]] = -1;
                }
                // Create the transitions to go up a state, except for the last state
                if (i != A.length - 1) {
                    A[i][values[i]] = i + 1;
                }
            }
            // cp.post(regular(w, A, 0, Arrays.asList(new Integer[] {4,5,6,7,8,9})));
            cp.post(regular(w, A));
            //#endregion

            //#region Cycle parity constraint
            for (int i = 0; i < values.length; i++) {
                IntVar numberOccurrences = makeIntVar(cp, 0, 2);
                numberOccurrences.remove(1);
                cp.post(among(w, values[i], numberOccurrences));
            }
            //#endregion

            //#region Molecular weight constraint
            IntVar weightTarget = makeIntVar(cp, minWeight, maxWeight);
            weightTarget.setName("Weight target");
            IntVar[] tokenWeights = makeIntVarArray(cp, wordLength, -40, 1259);
            for (int i = 0; i < wordLength; i++) {
                tokenWeights[i].setName("weight_" + i);
            }
            int[] tokenToWeight = new int[g.terminalCount()];
            for (int i = 0; i < g.terminalCount(); i++) {
                String token = g.tokenDecoder.get(i).toLowerCase();
                if (g.tokenWeight.containsKey(token)) {
                    tokenToWeight[i] = g.tokenWeight.get(token);
                } else {
                    tokenToWeight[i] = 0;
                }
            }
            for (int i = 0; i < wordLength; i++) {
                cp.post(element(tokenToWeight, w[i], tokenWeights[i]));
            }
            cp.post(sum(tokenWeights, weightTarget));
            //#endregion

            //#region Carbon percentage
            // int[] carbonAtoms = new int[2];
            // carbonAtoms[0] = g.tokenEncoder.get("C");
            // carbonAtoms[1] = g.tokenEncoder.get("c");
            // int lowerBound = Math.floorDiv(wordLength * minCarbon, 100);
            // int upperBound = Math.floorDiv(wordLength * maxCarbon, 100);
            // IntVar carbonRange = makeIntVar(cp, lowerBound, upperBound);
            // cp.post(among(w,carbonAtoms,carbonRange));
            //#endregion

            // C1OC1 C2OC2C3CC3S=CI
            // w[0].assign(g.tokenEncoder.get("C"));
            // w[1].assign(g.tokenEncoder.get("1"));
            // w[2].assign(g.tokenEncoder.get("O"));
            // w[3].assign(g.tokenEncoder.get("C"));
            // w[4].assign(g.tokenEncoder.get("1"));
            // w[5].assign(g.tokenEncoder.get("C"));
            // w[6].assign(g.tokenEncoder.get("2"));
            // w[7].assign(g.tokenEncoder.get("C"));
            // w[8].assign(g.tokenEncoder.get("2"));
            // w[9].assign(g.tokenEncoder.get("C"));
            // w[10].assign(g.tokenEncoder.get("3"));
            // w[11].assign(g.tokenEncoder.get("C"));
            // w[12].assign(g.tokenEncoder.get("C"));
            // w[13].assign(g.tokenEncoder.get("S"));
            // w[14].assign(g.tokenEncoder.get("O"));
            // w[15].assign(g.tokenEncoder.get("="));
            // w[16].assign(g.tokenEncoder.get("O"));
            // w[17].assign(g.tokenEncoder.get("C"));
            // w[18].assign(g.tokenEncoder.get("I"));
            // w[19].assign(g.tokenEncoder.get("_"));

            if (nCycles == 0) {
                cp.post(among(w, g.tokenEncoder.get("1"), makeIntVar(cp, 0,0)));
            } else if (nCycles > 0) {
                cp.post(among(w, g.tokenEncoder.get(Integer.toString(nCycles)), makeIntVar(cp, 2,2)));
                cp.post(among(w, g.tokenEncoder.get(Integer.toString(nCycles + 1)), makeIntVar(cp, 0,0)));
            }
            if (nBranches >= 0) {
                cp.post(among(w, g.tokenEncoder.get("("), makeIntVar(cp, nBranches, nBranches)));
            }

            //#region Solve
            // Sampling, replace w by branching vars if wanted
            // double fraction = 0.005;
            // IntVar[] branchingVars = cp.sample(fraction,w);

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
            }

            DFSearch dfs;
            switch (method) {
                case "minEntropy":
                    cp.setMode(PropaMode.SBP);
                    dfs = makeDfs(cp, minEntropy(w));
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
                System.out.println(word + " weight of " + sumWeight);
            });

            System.out.println("[INFO] Now solving");

            SearchStatistics stats;
            switch (method) {
                case "minEntropyBiasedWheel":
                case "maxMarginalRestart":
                case "impactRestart":
                case "domWdegRestart":
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

    private static void new_code(String filePath) {
        try {
            CFG g = new CFG(filePath);
            int wordLength = 15;
            Solver cp = makeSolver(false);
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);

            cp.post(grammar(w,g));

            int startingNb = 1;
            int endNb = 19;
            int nbCount = endNb - startingNb + 1;
            int[] values = new int[nbCount];
            for (int i = startingNb; i < startingNb + nbCount; i++) {
                String key = i < 10 ? Integer.toString(i) : '%' + Integer.toString(i);
                values[i-startingNb] = g.tokenEncoder.get(key);
            }

            int n = g.terminalCount();
            int[][] A = new int[nbCount][n];
            for (int i = 0; i < nbCount; i++) {
                // Default loop
                for (int j = 0; j < n; j++) {
                    A[i][j] = i;
                }
                // Disallow all numbers above the one associated to the current state
                for (int j = i + 1; j < values.length; j++) {
                    A[i][values[j]] = -1;
                }
                // Create the transitions to go up a state, except for the last state
                if (i != A.length - 1) {
                    A[i][values[i]] = i + 1;
                }
            }
            // cp.post(regular(w, A, 0, Arrays.asList(new Integer[] {4,5,6,7,8,9})));
            cp.post(regular(w, A));

            for (int i = 0; i < values.length; i++) {
                IntVar numberOccurrences = makeIntVar(cp, 0, 2);
                numberOccurrences.remove(1);
                cp.post(among(w, values[i], numberOccurrences));
            }

            //#region Sampling
            // cp.post(lessOrEqual(w[1],w[4])); // some other arbitrary constraint
            
            // sampling a "fraction" of the solutions
            // double fraction = 0.0001;
            // IntVar[] branchingVars = cp.sample(fraction,w);

            //randvarrandval
            // DFSearch dfs = makeDfs(cp, lexico(branchingVars));
            //#endregion
            cp.setMode(PropaMode.SBP);
            DFSearch dfs = makeDfs(cp, firstFail(w));
            
            dfs.onSolution(() -> {
                String word = "";
                for (int i = 0; i < wordLength; i++) {
                    word += g.tokenDecoder.get(w[i].min());
                }
                System.out.println(word);
            });

            SearchStatistics stats = dfs.solve(stat -> stat.numberOfSolutions() >= 10);
            System.out.println(stats.numberOfSolutions());
            // cp.fixPoint();
            // cp.setTraceBPFlag(true);
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

    private static void grammarComparator(
        String oldGrammarPath,
        String newGrammarPath,
        String dataSetPath,
        int startLine,
        int stopLine
    ) {
        int line = 0;
        BufferedReader reader;
        CFG g1;
        CFG g2;
        Solver cp1;
        Solver cp2;

        //#region Initialization
        try {
            reader = new BufferedReader(new FileReader(dataSetPath));
        
            if (!reader.ready()) {
                reader.close();
                throw new IOException();
            }

            g1 = new CFG(oldGrammarPath);
            cp1 = makeSolver(false);

            g2 = new CFG(oldGrammarPath);
            cp2 = makeSolver(false);

        } catch (Exception e) {
            System.out.println("Failed on initialization");
            System.out.println(e);
            return;
        }

        Vector<String> falseNegative = new Vector<>();
        Vector<String> falsePositive = new Vector<>();
        Vector<String> failedLines = new Vector<>();
        //#endregion

        //#region Move to start
        try {
            while(reader.ready() && line < startLine) {
                reader.readLine();
                line++;
            }
        } catch (Exception e) {
            System.out.println("Failed while going to start point");
            System.out.println(e);
        }
        //#endregion

        try {
            line++;
            Vector<String> tokens1 = new Vector<>();
            Vector<String> tokens2 = new Vector<>();
            while (reader.ready()) {
                String moleculeString = reader.readLine();
                char[] molecule = moleculeString.toCharArray();
                tokens1.clear();
                tokens2.clear();

                for (int i = 0; i < molecule.length; i++) {
                    if (molecule[i] == '%') {
                        tokens1.add(String.format("%c", molecule[i]));
                        tokens1.add(String.format("%c", molecule[i+1]));
                        tokens1.add(String.format("%c", molecule[i+2]));
                        tokens2.add(String.format("%c%c%c", molecule[i], molecule[i+1], molecule[i+2]));
                        i += 2; // Skips the next two chars
                        continue;
                    } else if (i != molecule.length - 1 && molecule[i] == 'C' && molecule[i+1] == 'l') {
                        String token = String.format("%c%c", molecule[i], molecule[i+1]);
                        tokens1.add(token);
                        tokens2.add(token);
                        i += 1;
                        continue;
                    } else if (i != molecule.length - 1 && molecule[i] == 'B' && molecule[i+1] == 'r') {
                        String token = String.format("%c%c", molecule[i], molecule[i+1]);
                        tokens1.add(token);
                        tokens2.add(token);
                        i += 1;
                        continue;
                    }
                    tokens1.add(String.format("%c", molecule[i]));
                    tokens2.add(String.format("%c", molecule[i]));
                }

                IntVar[] w1 = makeIntVarArray(cp1, tokens1.size(), 0, g1.terminalCount()-1);
                IntVar[] w2 = makeIntVarArray(cp2, tokens2.size(), 0, g2.terminalCount()-1);

                int i1 = 0;
                boolean skip = false;
                try {
                    while (i1 < tokens1.size()) {
                        Integer token = g1.tokenEncoder.get(tokens1.get(i1));
                        w1[i1].assign(token);
                        i1++;
                    }
                } catch (Exception e) {
                    failedLines.add(String.format("Grammar 1: %s", moleculeString));
                    skip = true;
                }

                int i2 = 0;
                try {
                    while (i2 < tokens2.size()) {
                        Integer token = g2.tokenEncoder.get(tokens2.get(i2));
                        w2[i2].assign(token);
                        i2++;
                    }
                } catch (Exception e) {
                    failedLines.add(String.format("Grammar 2: %s", moleculeString));
                    skip = true;
                }

                if (!skip) {
                    Boolean valid1 = true;
                    Boolean valid2 = true;
                    try {
                        cp1.post(grammar(w1, g1));
                    } catch (Exception e) {
                        valid1 = false;
                    }
                    try {
                        cp2.post(grammar(w2, g2));
                    } catch (Exception e) {
                        valid2 = false;
                    }
                    
                    // Add to lists for output
                    if (valid1 && !valid2) {
                        falseNegative.add(moleculeString);
                    } else if (!valid1 && valid2) {
                        falsePositive.add(moleculeString);
                    }
                }

                // System.out.println(String.format("%d %b %b", line, valid1, valid2));
                if (line % 100 == 0) {
                    System.out.println(line);
                }
                if (line % 1000 == 0) {
                    if (falseNegative.size() > 0 || falsePositive.size() > 0 || failedLines.size() > 0) {
                        try {
                            System.out.println("Writing");
                            FileWriter fstream = new FileWriter(String.format("verifications/out_%dk.txt", new Integer(line/1000)));
                            BufferedWriter out = new BufferedWriter(fstream);
                            out.write("********** FALSE NEGATIVES **********\n");
                            for (String m : falseNegative) {
                                out.write(m + "\n");
                            }
                            out.write("********** FALSE POSITIVES **********\n");
                            for (String m : falsePositive) {
                                out.write(m + "\n");
                            }
                            out.write("********** FAILED LINES **********\n");
                            for (String m : failedLines) {
                                out.write(m + "\n");
                            }
                            out.close();
                            falseNegative.clear();
                            falsePositive.clear();
                            failedLines.clear();
                        } catch (Exception e) {
                            System.out.println(String.format("Failed during writing"));
                            System.out.println(e);
                        }
                    } else {
                        System.out.println("No errors");
                    }
                    if (line == stopLine) {
                        reader.close();
                        return;
                    }
                }
                line++;
            }
            
            reader.close();
        } catch (Exception e) {
            System.out.println(String.format("Failed at line %d", line));
            System.out.println(e);
        }
    }
}

package minicpbp.examples;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.SearchStatistics;
import minicpbp.util.CFG;
import minicpbp.util.Production;
import minicpbp.util.io.InputReader;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Vector;

public class TestGrammar {
    public static void main(String[] args) {
        // new_code("data/moleculeCNF_v3.txt");

        // comparator(
        //     "data/moleculeCNF_v1.txt",
        //     "data/moleculeCNF_v5.txt",
        //     "big_data/ZINC250k.txt",
        //     0,
        //     24000
        // );

        // tester("data/moleculeCNF_v5.txt", "C(CCCCCC1)C1C", 5);
    }

    private static void tester(String filePath, String molecule, int decal) {
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
            System.out.println(String.format("%d atoms", wordLength - decal));
            Solver cp = makeSolver(false);
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
            for (int i = 0; i < tokens.size(); i++) {
                Integer token = g.tokenEncoder.get(tokens.get(i));
                w[i].assign(token);
            }

            try {
                cp.post(grammar(w,g));
            } catch (Exception e) {
                System.out.println("Failed during grammar constraint");
                throw e;
            }

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

            for (int i = 0; i < values.length; i++) {
                IntVar numberOccurrences = makeIntVar(cp, 0, 2);
                numberOccurrences.remove(1);
                cp.post(among(w, values[i], numberOccurrences));
            }

            System.out.println("Molecule is recognized");
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void new_code(String filePath) {
        try {
            CFG g = new CFG(filePath);
            int wordLength = 25;
            Solver cp = makeSolver(false);
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);

            cp.post(grammar(w,g));

            int startingNb = 4;
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

            // w[i].assign forces to a value

            //#region Sampling
            // cp.post(lessOrEqual(w[1],w[4])); // some other arbitrary constraint
            
            // sampling a "fraction" of the solutions
            // double fraction = 0.0001;
            // IntVar[] branchingVars = cp.sample(fraction,w);

            //randvarrandval
            // DFSearch dfs = makeDfs(cp, lexico(branchingVars));
            //#endregion
            DFSearch dfs = makeDfs(cp, randomVarRandomVal(w));
            
            dfs.onSolution(() -> {
                String word = "";
                for (int i = 0; i < wordLength; i++) {
                    word += g.tokenDecoder.get(w[i].min());
                }
                System.out.println(word);
            });

            SearchStatistics stats = dfs.solve(stat -> stat.numberOfSolutions() >= 10);
            System.out.println(stats.numberOfSolutions());
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void old() {
        InputReader reader = new InputReader("data/testing_old.txt");
        CFG g = new CFG(reader);
        int wordLength = 5;

        Solver cp = makeSolver();
        IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
        for (int i = 0; i < wordLength; i++)
            w[i].setName("w["+i+"]");

        cp.post(grammar(w,g));

        // cp.post(lessOrEqual(w[1],w[4])); // some other arbitrary constraint
        
        // sampling a "fraction" of the solutions
        // double fraction = 0.01;
        // IntVar[] branchingVars = cp.sample(fraction,w);

        cp.fixPoint();
        cp.setTraceBPFlag(true);
        cp.vanillaBP(3);

        // DFSearch dfs = makeDfs(cp, firstFail(branchingVars));
        // DFSearch dfs = makeDfs(cp, firstFail(w));
        /*
        DFSearch dfs = makeDfs(cp, maxMarginal(w));
        cp.setTraceSearchFlag(true);
        dfs.onSolution(() -> {
            for (int i = 0; i < wordLength; i++) {
                System.out.print(w[i].min());
            }
            System.out.println();
        });
        dfs.solve();
         */
    }

    private static void comparator(
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

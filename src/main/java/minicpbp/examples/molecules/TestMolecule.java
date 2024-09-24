package minicpbp.examples.molecules;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.engine.core.Solver.PropaMode;
import minicpbp.search.DFSearch;
import minicpbp.search.LDSearch;
import minicpbp.search.SearchStatistics;
import minicpbp.util.CFG;
import minicpbp.util.Procedure;
import minicpbp.util.exception.InconsistencyException;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Scanner;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;

import org.antlr.runtime.Token;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse.BodyHandlers;

public class TestMolecule {
    public static void main(String[] args) {
        // "CC(C)(C)c1ccc2occ(CC(=O)Nc3ccccc3F)c2c1"
        // CCCC135
        String molecule = "CCC1C1(CCCCC2)CCCCC2";
        testMoleculeValidity(
            "data/moleculeCNF_v7.txt",
            molecule,
            0
        );

        // grammarComparator(
        //     "data/moleculeCNF_v6.txt",
        //     "data/moleculeCNF_v7.txt",
        //     "big_data/ZINC250k.txt",
        //     0,
        //     3000
        // );
    }

    private static void testMoleculeValidity(String filePath, String molecule, int decal) {
        try {
            //#region Setup
            CFG g = new CFG(filePath);

            Vector<String> tokens = Tokenizers.tokenizeV7(molecule);

            int wordLength = tokens.size();
            Solver cp = makeSolver(false);
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
            for (int i = 0; i < wordLength; i++) {
                w[i].setName("w_" + i);
            }
            cp.setMode(PropaMode.SBP);
            //#endregion

            try {
                grammarConstraint(cp, w, g);
            } catch (Exception e) {
                System.out.println("Failed during grammar constraint");
                throw e;
            }

            try {
                cycleCountingConstraint(cp, w, g, 1, 8);
            } catch (Exception e) {
                System.out.println("Failed during regular constraint");
                throw e;
            }

            try {
                cycleParityConstraint(cp, w, g, 1, 8);
            } catch (Exception e) {
                System.out.println("Failed during parity constraint");
                throw e;
            }

            int i = 0;
            try {
                for (i = 0; i < tokens.size(); i++) {
                    Integer token = g.tokenEncoder.get(tokens.get(i));
                    w[i].assign(token);
                    cp.fixPoint(); // Might slow down, call at the end if that's the case
                    // cp.post(isEqual(w[i], token));
                }
            } catch (Exception e) {
                System.out.println(
                    String.format(
                        "Failed during setting at position %d for token %s",
                        i,
                        tokens.get(i)
                    )
                );
                throw e;
            }
            cp.fixPoint();

            // cp.setMode(PropaMode.SBP);
            // DFSearch dfs = makeDfs(cp, maxMarginalRandomTieBreak(w));
            // dfs.onSolution(() -> {
            //     String word = "";
            //     for (int iter = 0; iter < wordLength; iter++) {
            //         word += g.tokenDecoder.get(w[iter].min());
            //     }
            //     System.out.println(word);
            // });
            // System.out.println("[INFO] Now solving");
            // System.out.println(dfs.solve(stat -> stat.numberOfSolutions() == 1));

            System.out.println("[SUCCESS] " + molecule);
        } catch (Exception e) {
            System.out.println(e);
            System.out.println("[FAIL] " + molecule);
        }
    }

    private static void grammarConstraint(Solver cp, IntVar[] w, CFG g) throws FileNotFoundException, IOException {
        cp.post(grammar(w,g));
    }

    private static int[] getCycleTokens(CFG g, int start, int end) {
        int nbCount = end - start + 1;
        int[] cycleTokens = new int[nbCount];
        for (int i = start; i < start + nbCount; i++) {
            String key = i < 10 ? Integer.toString(i) : '%' + Integer.toString(i);
            cycleTokens[i-start] = g.tokenEncoder.get(key);
        }
        return cycleTokens;
    }

    private static void cycleCountingConstraint(Solver cp, IntVar[] w, CFG g, int start, int end) {
        int[] cycleTokens = getCycleTokens(g, start, end);

        int n = g.terminalCount();
        int[][] A = new int[cycleTokens.length][n];
        for (int i = 0; i < cycleTokens.length; i++) {
            // Default loop
            for (int j = 0; j < n; j++) {
                A[i][j] = i;
            }
            // Disallow all numbers above the one associated to the current state
            for (int j = i + 1; j < cycleTokens.length; j++) {
                A[i][cycleTokens[j]] = -1;
            }
            // Create the transitions to go up a state, except for the last state
            if (i != A.length - 1) {
                A[i][cycleTokens[i]] = i + 1;
            }
        }
        cp.post(regular(w, A));
    }

    private static void cycleParityConstraint(Solver cp, IntVar[] w, CFG g, int start, int end) {
        int[] cycleTokens = getCycleTokens(g, start, end);
        for (int i = 0; i < cycleTokens.length; i++) {
            IntVar numberOccurrences = makeIntVar(cp, 0, 2);
            numberOccurrences.remove(1);
            cp.post(among(w, cycleTokens[i], numberOccurrences));
        }
    }
}

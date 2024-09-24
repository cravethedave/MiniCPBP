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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse.BodyHandlers;

public class TestMoleculeBatch {
    public static void main(String[] args) {
        bulkMolVal("data/moleculeCNF_v7.txt", "big_data/ZINC250k.txt");
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
        // int startingNb = 1;
        // int endNb = 19;
        // int nbCount = endNb - startingNb + 1;
        // int[] values = new int[nbCount];
        // for (int i = startingNb; i < startingNb + nbCount; i++) {
        //     String key = i < 10 ? Integer.toString(i) : '%' + Integer.toString(i);
        //     values[i-startingNb] = g.tokenEncoder.get(key);
        // }

        // regular
        // int n = g.terminalCount();
        // int[][] A = new int[nbCount][n];
        // for (int i = 0; i < nbCount; i++) {
        //     // Default loop
        //     for (int j = 0; j < n; j++) {
        //         A[i][j] = i;
        //     }
        //     // Disallow all numbers above the one associated to the current state
        //     for (int j = i + 1; j < values.length; j++) {
        //         A[i][values[j]] = -1;
        //     }
        //     // Create the transitions to go up a state, except for the last state
        //     if (i != A.length - 1) {
        //         A[i][values[i]] = i + 1;
        //     }
        // }
        //#endregion

        System.out.println("[INFO] Done setup");

        int processed = 0;
        float failures = 0.0f;
        Vector<String> unrecognized_molecules = new Vector<>();
        Vector<String> failed_molecules = new Vector<>();
        for (Vector<String> tokens : molecules) {
            String molString = tokens.toString();
            if (processed % 10 == 0) {
                System.out.println("[INFO] Failed " + failures + " of the " + processed + " processed");
            }
            processed++;
            int wordLength = tokens.size();
            Solver cp = makeSolver(false);
            IntVar[] w = makeIntVarArray(cp, wordLength, 0, g.terminalCount()-1);
            for (int i = 0; i < wordLength; i++) {
                w[i].setName("w_" + i);
            }

            //#region Constraints setting
            try {
                cp.post(grammar(w,g));
                // cp.post(regular(w, A));
                // for (int i = 0; i < values.length; i++) {
                //     IntVar numberOccurrences = makeIntVar(cp, 0, 2);
                //     numberOccurrences.remove(1);
                //     cp.post(among(w, values[i], numberOccurrences));
                // }
            } catch (Exception e) {
                System.out.println("[ERROR] Failed during constraint application. Should not happen.");
                continue;
            }
            //#endregion

            //#region Variable Setting
            try {
                for (int i = 0; i < tokens.size(); i++) {
                    if (!g.tokenEncoder.containsKey(tokens.get(i))) {
                        System.out.println("[WARN] Grammar does not contain " + tokens.get(i));
                        unrecognized_molecules.add(molString);
                        failures++;
                        break;
                    }
                    Integer token = g.tokenEncoder.get(tokens.get(i));
                    w[i].assign(token);
                }
            } catch (Exception e) {
                System.out.println("[ERROR] Molecule unrecognized: " + molString);
                failed_molecules.add(molString);
                failures++;
            }
            //#endregion
        }
        
        System.out.println("[INFO] Failed " + failures + " out of " + molecules.size() + ". " + failures/molecules.size() * 100 + "% success rate.");
        System.out.println(Math.round(failures));
        System.out.println("unrecognized_molecules");
        System.out.println(unrecognized_molecules);
        System.out.println("failed_molecules");
        System.out.println(failed_molecules);
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
}

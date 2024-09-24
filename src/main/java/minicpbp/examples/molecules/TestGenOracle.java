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
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse.BodyHandlers;

public class TestGenOracle {
    public static void main(String[] args) {
        // oracleRun(
        //     "data/moleculeCNF_v7.txt",
        //     20,
        //     "maxMarginal",
        //     4750,
        //     5000,
        //     1,
        //     2
        // );
        oracleRun(
            "data/moleculeCNF_v7.txt",
            Integer.valueOf(args[0]),
            args[1],
            Integer.valueOf(args[2]),
            Integer.valueOf(args[3]),
            Integer.valueOf(args[4]),
            Integer.valueOf(args[5])
        );
    }

    private static void oracleRun(
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
            
            
            HttpClient client = HttpClient.newHttpClient();

            String moleculeSoFar = "";
            for (int i = 0; i < wordLength; i++) {
                HttpRequest request = HttpRequest.newBuilder()
                  .uri(URI.create("http://localhost:5000/token"))
                  .POST(HttpRequest.BodyPublishers.ofString(moleculeSoFar))
                  .build();
                String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
                
                break;
                // cp.post(oracle(w[i], null, null));
                // moleculeSoFar += "";
            }

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static CompletableFuture<String> testRequest() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:5000/token"))
            .POST(HttpRequest.BodyPublishers.ofString("Hello World"))
            .build();
        return client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body);
    }

}

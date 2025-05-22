package minicpbp.examples.molecules;

import minicpbp.engine.core.Constraint;
import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.engine.core.Solver.PropaMode;
import minicpbp.search.DFSearch;
import minicpbp.search.LDSearch;
import minicpbp.search.SearchStatistics;
import minicpbp.util.CFG;
import minicpbp.util.exception.InconsistencyException;

import static minicpbp.cp.Factory.*;
import static minicpbp.cp.BranchingScheme.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

public class TestGenOracle {
    static String SERVER_ADDRESS = "http://localhost:5000/token";

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

        // nlpOracleModel(
        // // constraintOnlyModel(
        //     "data/moleculeCNF_v7.txt",
        //     Integer.valueOf(args[0]),
        //     args[1],
        //     Integer.valueOf(args[2]),
        //     Integer.valueOf(args[3]),
        //     Float.valueOf(args[4])
        // );

        // nlpOracleModel(
        // // constraintOnlyModel(
        //     "data/moleculeCNF_v7.txt",
        //     Integer.valueOf(args[0]),
        //     args[1],
        //     Integer.valueOf(args[2]),
        //     Integer.valueOf(args[3]),
        //     Double.valueOf(args[4])
        // );

        // variableWeightMaskModel(
        // // constraintOnlyModel(
        //     "data/moleculeCNF_v7.txt",
        //     Integer.valueOf(args[0]),
        //     args[1],
        //     Integer.valueOf(args[2]),
        //     Integer.valueOf(args[3]),
        //     args[4]
        // );

        setWeightGenerationModel_NNCPBP(
            "data/moleculeCNF_v7.txt",
            Integer.valueOf(args[0]),
            args[1],
            Integer.valueOf(args[2]),
            Integer.valueOf(args[3]),
            1.0
        );
    }

    /**
     * 
     * @param g The grammar, while called CFG it is in Chomsky's Normal Form
     * @param moleculeSoFar The molecule as a string
     * @return a HashMap linking each token's int id to a probability as a double
     */
    private static HashMap<Integer, Double> getModelProbabilities(
        CFG g,
        String moleculeSoFar
    ) {
        //#region Makes the request
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(SERVER_ADDRESS))
            .POST(HttpRequest.BodyPublishers.ofString(moleculeSoFar))
            .build();
        String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
        //#endregion

        //#region Parse the response into a HashMap
        response = response.replace("}", "");

        HashMap<Integer, Double> tokenToScoreNLP = new HashMap<>();
        double minScore = Double.MAX_VALUE;
        for (String line : response.split(",")) {
            String[] lineValues = line.split(":");
            String tokenString = lineValues[0].split("\"")[1];
            if (tokenString.equals("\\\\")) {
                tokenString = "\\";
            }
            if (!g.tokenEncoder.containsKey(tokenString)) {
                System.out.println("Token not in grammar " + tokenString);
                continue;
            }
            int token = g.tokenEncoder.get(tokenString);
            double score = Double.parseDouble(lineValues[1]);

            if (score < minScore) {
                minScore = score;
            }

            tokenToScoreNLP.put(token, score);
        }
        //#endregion
        
        
        //#region Flattens values to ensure a sum of 1. Should be redundant
        HashMap<Integer, Double> tokenToScoreMap = new HashMap<>();
        double scoreSum = 0;
        for (double v : tokenToScoreNLP.values()) {
            scoreSum += v;
        }

        for (int t : tokenToScoreNLP.keySet()) {
            tokenToScoreMap.put(t, tokenToScoreNLP.get(t) / scoreSum);
        }
        //#endregion

        return tokenToScoreMap;
    }

    private static void constraintOnlyModel(
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
            
            // Apply constraints
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,8);
            GenConstraints.cycleParityConstraint(cp,w,g,1,8);
            GenConstraints.moleculeWeightConstraint(cp,w,tokenWeights,weightTarget,g);
            
            String moleculeSoFar = "";
            int realLength = wordLength;
            Double logSumProbs = 0.0;
            for (int i = 0; i < wordLength; i++) {
                HashMap<Integer, Double> flattenedNLPScores = getModelProbabilities(g, moleculeSoFar);

                cp.fixPoint();
                cp.beliefPropa();

                Vector<Integer> domainTokens = new Vector<>();
                HashMap<Integer, Double> oracleScores = new HashMap<>();
                for (int t = 0; t < g.terminalCount(); t++) {
                    if (w[i].contains(t)) {
                        domainTokens.add(t);
                        oracleScores.put(t, w[i].marginal(t));
                    }
                }

                domainTokens.sort((Integer left, Integer right) -> oracleScores.get(right).compareTo(oracleScores.get(left)));

                for (int j = 5; j < domainTokens.size(); j++) {
                    w[i].remove(domainTokens.get(j));
                }

                w[i].assign(w[i].biasedWheelValue());
                int chosen = w[i].min();
                if (chosen == g.tokenEncoder.get("_")) {
                    for (int j = i+1; j < wordLength; j++) {
                        w[j].assign(chosen);
                    }
                    realLength = i;
                    break;
                }
                if (flattenedNLPScores.containsKey(chosen)) {
                    logSumProbs += Math.log(flattenedNLPScores.get(chosen));
                } else {
                    System.out.println("Chose a value not in the nlp model");
                    logSumProbs = -Double.MAX_VALUE;
                }
                moleculeSoFar += g.tokenDecoder.get(w[i].min());
                System.out.println(moleculeSoFar);
            }
            double perplexityScore = Math.exp(-logSumProbs / realLength);
            System.out.println("Final molecule " + moleculeSoFar + " with a weight of " + weightTarget.min());
            System.out.println("Perplexity is of " + perplexityScore);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void nlpOracleModel(
        String filePath,
        int wordLength,
        String method,
        int minWeight,
        int maxWeight,
        double oracleWeight
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
            
            String perplexityResults = "";
            
            String moleculeSoFar = "";
            int realLength = wordLength;
            Double logSumProbs = 0.0;
            for (int i = 0; i < wordLength; i++) {
                // Makes the request
                HashMap<Integer, Double> flattenedNLPScores = getModelProbabilities(g, moleculeSoFar);

                int[] tokens = new int[g.terminalCount()];
                double[] scores = new double[g.terminalCount()];
                for (int t : flattenedNLPScores.keySet()) {
                    tokens[t] = t;
                    scores[t] = flattenedNLPScores.get(t);
                }

                // post oracle
                Constraint oracleConstraint = oracle(w[i], tokens, scores);
                oracleConstraint.setWeight(oracleWeight);
                cp.post(oracleConstraint);
                cp.fixPoint();
                cp.beliefPropa();

                Vector<Integer> domainTokens = new Vector<>();
                HashMap<Integer, Double> oracleScores = new HashMap<>();
                for (int t = 0; t < g.terminalCount(); t++) {
                    if (w[i].contains(t)) {
                        domainTokens.add(t);
                        oracleScores.put(t, w[i].marginal(t)); 
                    }
                }

                domainTokens.sort((Integer left, Integer right) -> oracleScores.get(right).compareTo(oracleScores.get(left)));

                for (int j = 5; j < domainTokens.size(); j++) {
                    w[i].remove(domainTokens.get(j));
                }

                int chosen = w[i].biasedWheelValue();
                double modelProb = w[i].marginal(chosen);
                w[i].assign(chosen);
                if (chosen == g.tokenEncoder.get("_")) {
                    for (int j = i+1; j < wordLength; j++) {
                        w[j].assign(chosen);
                    }
                    realLength = i;
                    break;
                }
                if (flattenedNLPScores.containsKey(chosen)) {
                    perplexityResults += Double.toString(modelProb) + "/";
                    perplexityResults += Double.toString(flattenedNLPScores.get(chosen)) + ",";
                    logSumProbs += Math.log(flattenedNLPScores.get(chosen));
                } else {
                    System.out.println("Chose a value not in the nlp model");
                    logSumProbs = -Double.MAX_VALUE;
                }
                moleculeSoFar += g.tokenDecoder.get(w[i].min());
                System.out.println(moleculeSoFar);
            }
            double perplexityScore = Math.exp(-logSumProbs / realLength);

            perplexityResults += '\n';

            FileWriter writer = new FileWriter("./perplexity.txt", true);
            writer.append(perplexityResults);
            writer.close();

            System.out.println("Final molecule " + moleculeSoFar + " with a weight of " + weightTarget.min() + " perplexity is of " + perplexityScore);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void updateSolver(Solver cp) {
        cp.fixPoint();
        // cp.beliefPropa();
        cp.vanillaBP(4);
    }

    private static void variableWeightMaskModel(
        String filePath,
        int wordLength,
        String method,
        int minWeight,
        int maxWeight,
        String chosenMol
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
            float weightDegradation = 0.982324333f;
            //#endregion
            
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,8);
            GenConstraints.cycleParityConstraint(cp,w,g,1,8);
            GenConstraints.moleculeWeightConstraint(cp,w,tokenWeights,weightTarget,g);
            
            String perplexityResults = "";

            int realLength = wordLength;
            Double logSumProbs = 0.0;

            String[] splitMol = chosenMol.split(",");
            String moleculeSoFar = String.join("", splitMol);

            // Assigns variables that start with a value to help inference later
            try {
                for (int i = 0; i < wordLength; i++) {
                    splitMol[i] = splitMol[i].replace("\n", "").strip();
                    if (!splitMol[i].equals("<mask>")) {
                        w[i].assign(g.tokenEncoder.get(splitMol[i]));
                    }
                }
                updateSolver(cp);
            } catch (Exception e) {
                System.out.println("[RESTART] Crashed before it started " + moleculeSoFar);
                throw e;
            }

            for (int i = 0; i < wordLength; i++) {
                if (!splitMol[i].equals("<mask>")) continue;

                // Makes the request
                HashMap<Integer, Double> flattenedNLPScores = getModelProbabilities(g, moleculeSoFar);

                int[] tokens = new int[g.terminalCount()];
                double[] scores = new double[g.terminalCount()];
                for (int t : flattenedNLPScores.keySet()) {
                    tokens[t] = t;
                    scores[t] = flattenedNLPScores.get(t);
                }

                // post oracle
                Constraint oracleConstraint = oracle(w[i], tokens, scores);
                oracleConstraint.setWeight(Math.pow(weightDegradation, i + 1));
                cp.post(oracleConstraint);
                updateSolver(cp);

                //#region Top 5 selection
                // Vector<Integer> domainTokens = new Vector<>();
                // HashMap<Integer, Double> oracleScores = new HashMap<>();
                // for (int t = 0; t < g.terminalCount(); t++) {
                //     if (w[i].contains(t)) {
                //         domainTokens.add(t);
                //         oracleScores.put(t, w[i].marginal(t)); 
                //     }
                // }

                // domainTokens.sort((Integer left, Integer right) -> oracleScores.get(right).compareTo(oracleScores.get(left)));

                // for (int j = 5; j < domainTokens.size(); j++) {
                //     w[i].remove(domainTokens.get(j));
                // }
                // w[i].normalizeMarginals();
                //#endregion

                int chosen = w[i].biasedWheelValue();
                double modelProb = w[i].marginal(chosen);
                w[i].assign(chosen);
                // if (chosen == g.tokenEncoder.get("_")) {
                //     for (int j = i+1; j < wordLength; j++) {
                //         moleculeSoFar += '_';
                //         w[j].assign(chosen);
                //     }
                //     realLength = i;
                //     break;
                // }
                if (flattenedNLPScores.containsKey(chosen)) {
                    perplexityResults += Double.toString(modelProb) + "/";
                    perplexityResults += Double.toString(flattenedNLPScores.get(chosen)) + ",";
                    logSumProbs += Math.log(flattenedNLPScores.get(chosen));
                } else {
                    System.out.println("Chose a value not in the nlp model");
                    logSumProbs = -Double.MAX_VALUE;
                }
                splitMol[i] = g.tokenDecoder.get(w[i].min());
                moleculeSoFar = String.join("", splitMol);
                System.out.println(moleculeSoFar);
            }
            double perplexityScore = Math.exp(-logSumProbs / realLength);
            perplexityResults += '\n';

            FileWriter writer = new FileWriter("./perplexity.txt", true);
            writer.append(perplexityResults);
            writer.close();

            System.out.println("Final molecule " + moleculeSoFar + " with a weight of " + weightTarget.min() + " perplexity is of " + perplexityScore);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void setWeightGenerationModel_NNCPBP(
        String filePath,
        int wordLength,
        String method,
        int minWeight,
        int maxWeight,
        double oracleWeight
    ) {
        long startTime = System.currentTimeMillis()/1000;
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
            
            int realLength = wordLength;
            Double logSumProbs = 0.0;

            String moleculeSoFar = "<s>";
            String perplexityResults = "";
            String input = "<s>";

            for (int i = 0; i < wordLength; i++) {
                if (moleculeSoFar.length() < input.length()) {
                    char toAdd = input.charAt(i+3);
                    moleculeSoFar += toAdd;
                    w[i].assign(g.tokenEncoder.get(String.valueOf(toAdd)));
                    cp.fixPoint();
                    continue;
                }
                // Fills the rest with padding
                if (moleculeSoFar.endsWith("_")) {
                    realLength = i;
                    for (int j = i; j < wordLength; j++) {
                        moleculeSoFar += "_";
                        w[i].assign(g.tokenEncoder.get("_"));
                        cp.fixPoint();
                    }
                    break;
                }

                // Makes the request
                HashMap<Integer, Double> flattenedNLPScores = getModelProbabilities(g, moleculeSoFar);
                // System.out.println(flattenedNLPScores.toString());

                int[] tokens = new int[flattenedNLPScores.size()];
                double[] scores = new double[flattenedNLPScores.size()];
                int counter = 0;
                for (int t : flattenedNLPScores.keySet()) {
                    tokens[counter] = t;
                    scores[counter] = flattenedNLPScores.get(t);
                    counter++;
                }

                // post oracle
                Constraint oracleConstraint = oracle(w[i], tokens, scores);
                oracleConstraint.setWeight(oracleWeight);
                cp.post(oracleConstraint);
                updateSolver(cp);
                w[i].normalizeMarginals();
                // Forcibly crashes if all marginals are 0
                if (w[i].maxMarginal() == 0.0) { 
                    throw new InconsistencyException();
                }
                // System.out.println(w[i].toString());

                int chosen = w[i].biasedWheelValue();
                // int chosen = w[i].valueWithMaxMarginal();
                double chosenOdds = w[i].marginal(chosen);
                w[i].assign(chosen);
                if (flattenedNLPScores.containsKey(chosen)) {
                    perplexityResults += String.valueOf(chosenOdds) + "/" + String.valueOf(flattenedNLPScores.get(chosen)) + ",";
                    logSumProbs += Math.log(flattenedNLPScores.get(chosen));
                } else {
                    System.out.println("Chose a value not in the nlp model");
                    logSumProbs = -Double.MAX_VALUE;
                }
                
                moleculeSoFar += g.tokenDecoder.get(w[i].min());
                System.out.println(moleculeSoFar);
            }
            perplexityResults += '\n';
            double perplexityScore = Math.exp(-logSumProbs / realLength);
            // System.out.println("Final molecule " + moleculeSoFar + " with a weight of " + weightTarget.min() + " perplexity is of " + perplexityScore);
            
            FileWriter resultsWriter = new FileWriter("./results.txt", true);
            resultsWriter.write(
                moleculeSoFar + "," +
                String.valueOf(weightTarget.min()) + "," +
                String.valueOf(perplexityScore) + "," +
                String.valueOf(System.currentTimeMillis()/1000 - startTime) +
                "\n"
            );
            resultsWriter.close();

            FileWriter perplexityWriter = new FileWriter("./perplexity.txt", true);
            perplexityWriter.write(perplexityResults);
            perplexityWriter.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void setWeightGenerationModel_NNCP(
        String filePath,
        int wordLength,
        String method,
        int minWeight,
        int maxWeight,
        double oracleWeight
    ) {
        long startTime = System.currentTimeMillis()/1000;
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

            String moleculeSoFar = "<s>";

            for (int i = 0; i < wordLength; i++) {
                // Fills the rest with padding
                if (moleculeSoFar.endsWith("_")) {
                    for (int j = i; j < wordLength; j++) {
                        moleculeSoFar += "_";
                        w[i].assign(g.tokenEncoder.get("_"));
                        cp.fixPoint();
                    }
                    break;
                }

                // Makes the request
                // System.out.println("here");
                HashMap<Integer, Double> flattenedNLPScores = getModelProbabilities(g, moleculeSoFar);
                // System.out.println(flattenedNLPScores.toString());

                // Propagates constraints to determine current varialbe's values
                cp.fixPoint();

                // Removes all values that were filtered
                for (int j = 0; j < g.terminalCount(); j++) {
                    if (w[i].contains(j)) {
                        continue;
                    }
                    flattenedNLPScores.remove(j);
                }
                // System.out.println(w[i].toString());

                // Manually throw an error if there are no possible choices left
                if (flattenedNLPScores.size() == 0) {
                    throw new InconsistencyException();
                }

                // Normalize the distribution and extract needed values
                Double summedProbs = 0.0;
                int[] availableTokens = new int[flattenedNLPScores.size()];
                for (Double v : flattenedNLPScores.values()) {
                    summedProbs += v;
                }
                int addedKeys = 0;
                Double max = 0.0;
                for (int k : flattenedNLPScores.keySet()) {
                    flattenedNLPScores.put(k, flattenedNLPScores.get(k)/summedProbs);
                    if (flattenedNLPScores.get(k) > max) {
                        max = flattenedNLPScores.get(k);
                    }
                    availableTokens[addedKeys] = k;
                    addedKeys++;
                }

                // Biased wheel selection
                Random rand = cp.getRandomNbGenerator();
                int chosen = availableTokens[rand.nextInt(availableTokens.length)];
                while (rand.nextDouble() > flattenedNLPScores.get(chosen)/max) {
                    chosen = availableTokens[rand.nextInt(availableTokens.length)];                    
                }

                // Max marginal selection
                // int chosen = 0;
                // for (int k : flattenedNLPScores.keySet()) {
                //     if (flattenedNLPScores.get(k) == max) {
                //         chosen = k;
                //     }
                // }

                // Assign values
                w[i].assign(chosen);                
                moleculeSoFar += g.tokenDecoder.get(w[i].min());
                System.out.println(moleculeSoFar);
            }
            
            FileWriter resultsWriter = new FileWriter("./results.txt", true);
            resultsWriter.write(
                moleculeSoFar + "," +
                String.valueOf(weightTarget.min()) + "," +
                String.valueOf(System.currentTimeMillis()/1000 - startTime) +
                "\n"
            );
            resultsWriter.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void setWeightGenerationModel_CPBP(
        String filePath,
        int wordLength,
        String method,
        int minWeight,
        int maxWeight,
        double oracleWeight
    ) {
        long startTime = System.currentTimeMillis()/1000;
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
            
            int realLength = wordLength;
            double[] chosenOdds = new double[wordLength];
            String moleculeSoFar = "";
            // Generate the molecule
            for (int i = 0; i < wordLength; i++) {
                if (moleculeSoFar.endsWith("_")) {
                    realLength = i;
                    for (int j = i; j < wordLength; j++) {
                        moleculeSoFar += "_";
                        w[i].assign(g.tokenEncoder.get("_"));
                        cp.fixPoint();
                    }
                    break;
                }
                
                updateSolver(cp);
                w[i].normalizeMarginals();
                // Forcibly crashes if all marginals are 0
                if (w[i].maxMarginal() == 0.0) {
                    throw new InconsistencyException();
                }

                int chosen = w[i].biasedWheelValue();
                chosenOdds[i] = w[i].marginal(chosen);
                w[i].assign(chosen);
                moleculeSoFar += g.tokenDecoder.get(chosen);
                System.out.println(moleculeSoFar);
            }
            // System.out.println("Final molecule " + moleculeSoFar + " with a weight of " + weightTarget.min() + " perplexity is of " + perplexityScore);
            long runTime = System.currentTimeMillis()/1000 - startTime;

            //#region Calculate the perplexity of the molecule
            Double logSumProbs = 0.0;
            String perplexityResults = "";
            moleculeSoFar = "<s>";

            for (int i = 0; i < wordLength; i++) {
                System.out.println(moleculeSoFar);
                HashMap<Integer, Double> flattenedNLPScores = getModelProbabilities(g, moleculeSoFar);
                int chosen = w[i].min();
                if (chosen == g.tokenEncoder.get("_")) {
                    realLength = i;
                    for (int j = i; j < wordLength; j++) {
                        moleculeSoFar += "_";
                    }
                    break;
                }

                if (flattenedNLPScores.containsKey(chosen)) {
                    perplexityResults += String.valueOf(chosenOdds[i]) + "/" + String.valueOf(flattenedNLPScores.get(chosen)) + ",";
                    logSumProbs += Math.log(flattenedNLPScores.get(chosen));
                } else {
                    System.out.println("Chose a value not in the nlp model");
                    logSumProbs = -Double.MAX_VALUE;
                }
                moleculeSoFar += g.tokenDecoder.get(w[i].min());
            }
            double perplexityScore = Math.exp(-logSumProbs / realLength);
            perplexityResults += '\n';
            //#endregion

            FileWriter resultsWriter = new FileWriter("./results.txt", true);
            resultsWriter.write(
                moleculeSoFar + "," +
                String.valueOf(weightTarget.min()) + "," +
                String.valueOf(perplexityScore) + "," +
                String.valueOf(runTime) +
                "\n"
            );
            resultsWriter.close();

            FileWriter perplexityWriter = new FileWriter("./perplexity.txt", true);
            perplexityWriter.write(perplexityResults);
            perplexityWriter.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }
    
    private static void setWeightGenerationModel_CPBPBackTrack(
        String filePath,
        int wordLength,
        String method,
        int minWeight,
        int maxWeight,
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
            GenConstraints.moleculeWeightConstraint(cp, w, tokenWeights, makeIntVar(cp, minWeight, maxWeight), g);
            // Other constraints
            IntVar logPEstimate = makeIntVar(cp, 0, 0);
            logPEstimate.setName("LogP estimate");
   
            String fileName = "results_" + method + "_sz" + wordLength;
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
                    solveLDS(cp, w, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
                    break;
                default:
                    solveDFS(cp, w, g, method, tokenWeights, logPEstimate, numSolutions, limitInSeconds, fileName);
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

    private static void variableWeightGenerationModel(
        String filePath,
        int wordLength,
        String method,
        int minWeight,
        int maxWeight
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
            float weightDegradation = 0.982324333f;
            //#endregion
            
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,8);
            GenConstraints.cycleParityConstraint(cp,w,g,1,8);
            GenConstraints.moleculeWeightConstraint(cp,w,tokenWeights,weightTarget,g);
            
            int realLength = wordLength;
            Double logSumProbs = 0.0;

            String moleculeSoFar = "<s>";

            for (int i = 0; i < wordLength; i++) {
                // Makes the request
                HashMap<Integer, Double> flattenedNLPScores = getModelProbabilities(g, moleculeSoFar);

                int[] tokens = new int[g.terminalCount()];
                double[] scores = new double[g.terminalCount()];
                for (int t : flattenedNLPScores.keySet()) {
                    tokens[t] = t;
                    scores[t] = flattenedNLPScores.get(t);
                }

                // post oracle
                Constraint oracleConstraint = oracle(w[i], tokens, scores);
                oracleConstraint.setWeight(Math.pow(weightDegradation, i + 1));
                cp.post(oracleConstraint);
                updateSolver(cp);

                int chosen = w[i].biasedWheelValue();
                w[i].assign(chosen);
                if (flattenedNLPScores.containsKey(chosen)) {
                    logSumProbs += Math.log(flattenedNLPScores.get(chosen));
                } else {
                    System.out.println("Chose a value not in the nlp model");
                    logSumProbs = -Double.MAX_VALUE;
                }
                
                moleculeSoFar += g.tokenDecoder.get(w[i].min());
                System.out.println(moleculeSoFar);
            }
            double perplexityScore = Math.exp(-logSumProbs / realLength);
            System.out.println("Final molecule " + moleculeSoFar + " with a weight of " + weightTarget.min() + " perplexity is of " + perplexityScore);
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}

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

import java.io.FileNotFoundException;
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
    static String TOKEN_ADDRESS = "http://localhost:5000/token";
    static String PPL_ADDRESS = "http://localhost:5000/ppl";
    static String FILE_PATH = "data/moleculeCNF_v7.txt";
    static int WORD_LENGTH = 40;
    static int MIN_MOL_WEIGHT = 2500;
    static int MAX_MOL_WEIGHT = 2750;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Please give the method name to run as well as the oracle weight as shown in the README");
            return;
        }

        switch (args[0]) {
            case "cpbp_back":
                cpbp_back();
                break;
            case "cpbp":
                cpbp_no_back();
                break;
            case "gpt":
                gpt();
                break;
            case "gpt_cp":
                gpt_cp();
                break;
            case "gpt_cpbp":
                gpt_cpbp(Float.valueOf(args[1]));
                break;
            default:
                System.out.println("Unrecognized method name. The recognized methods are:\n\tcpbp_back\n\tcpbp\n\tgpt\n\tgpt_cp\n\tgpt_cpbp");
        }
    }

    protected static class BaseModel {
        static Solver cp;
        static CFG g;
        static IntVar[] w;
        static IntVar[] tokenWeights;

        static void initialization() throws FileNotFoundException, IOException {
            cp = makeSolver(false);
            g = new CFG(FILE_PATH);

            // Creates the array of token variables
            w = makeIntVarArray(cp, WORD_LENGTH, 0, g.terminalCount()-1);
            for (int i = 0; i < WORD_LENGTH; i++) {
                w[i].setName("token_" + i);
            }

            // Creates the array of weight variables
            int minAtomWeight = Collections.min(g.tokenWeight.values());
            int maxAtomWeight = Collections.max(g.tokenWeight.values());
            tokenWeights = makeIntVarArray(cp, WORD_LENGTH, minAtomWeight, maxAtomWeight);
            for (int i = 0; i < WORD_LENGTH; i++) {
                tokenWeights[i].setName("weight_" + i);
            }
        }
    };

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
            .uri(URI.create(TOKEN_ADDRESS))
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

    private static double getMoleculePerplexity(String molecule) {
        //#region Makes the request
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(PPL_ADDRESS))
            .POST(HttpRequest.BodyPublishers.ofString(molecule))
            .build();
        String response = client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).join();
        //#endregion
        return Double.valueOf(response);
    }

    private static void updateSolver(Solver cp) {
        cp.fixPoint();
        cp.vanillaBP(4);
    }

    private static void gpt() {
        long startTime = System.currentTimeMillis();
        try {
            //#region Base initialization
            BaseModel.initialization();
            // // Create variables to shorten access
            Solver cp = BaseModel.cp;
            CFG g = BaseModel.g;
            IntVar[] w = BaseModel.w;
            IntVar[] tokenWeights = BaseModel.tokenWeights;
            //#endregion
            
            // No constraints yet to avoid slowing down the process

            //#region Solving
            String moleculeSoFar = "<s>";
            Double logSumProbs = 0.0;
            for (int i = 0; i < WORD_LENGTH; i++) {
                // Makes the request
                HashMap<Integer, Double> flattenedNLPScores = getModelProbabilities(g, moleculeSoFar);

                // Normalize the distribution and extract needed values
                int[] availableTokens = new int[flattenedNLPScores.size()];
                int addedKeys = 0;
                Double max = 0.0;
                for (int k : flattenedNLPScores.keySet()) {
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

                // Assign values
                w[i].assign(chosen);
                moleculeSoFar += g.tokenDecoder.get(w[i].min());
                if (chosen == g.tokenEncoder.get("_")) {
                    for (int j = i+1; j < WORD_LENGTH; j++) {
                        moleculeSoFar += "_";
                    }
                    break;
                }
            }
            //#endregion

            long runTime = System.currentTimeMillis() - startTime;

            //#region Validate result
            GenConstraints.grammarConstraint(cp, w, g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,8);
            GenConstraints.cycleParityConstraint(cp,w,g,1,8);
            GenConstraints.moleculeWeightConstraint(cp, w, tokenWeights, makeIntVar(cp, MIN_MOL_WEIGHT, MAX_MOL_WEIGHT), g);
            //#endregion

            System.out.println(moleculeSoFar + "," + String.valueOf(runTime/1000.0) + "," + String.valueOf(getMoleculePerplexity(moleculeSoFar)));
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void gpt_cpbp(float oracleWeight) {
        long startTime = System.currentTimeMillis();
        try {
            //#region Base initialization
            BaseModel.initialization();
            // Create variables to shorten access
            Solver cp = BaseModel.cp;
            CFG g = BaseModel.g;
            IntVar[] w = BaseModel.w;
            IntVar[] tokenWeights = BaseModel.tokenWeights;
            //#endregion
            
            //#region Constraints
            // Smiles Validity
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,8);
            GenConstraints.cycleParityConstraint(cp,w,g,1,8);

            // Other constraints
            GenConstraints.moleculeWeightConstraint(cp, w, tokenWeights, makeIntVar(cp, MIN_MOL_WEIGHT, MAX_MOL_WEIGHT), g);
            //#endregion
            
            //#region Solving
            int realLength = WORD_LENGTH;
            Double logSumProbs = 0.0;

            String moleculeSoFar = "<s>";
            String perplexityResults = "";
            String input = "<s>";

            for (int i = 0; i < WORD_LENGTH; i++) {
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
                    for (int j = i; j < WORD_LENGTH; j++) {
                        moleculeSoFar += "_";
                        w[i].assign(g.tokenEncoder.get("_"));
                        cp.fixPoint();
                    }
                    break;
                }

                // Makes the request
                HashMap<Integer, Double> flattenedNLPScores = getModelProbabilities(g, moleculeSoFar);

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

                int chosen = w[i].biasedWheelValue();
                w[i].assign(chosen);
                moleculeSoFar += g.tokenDecoder.get(w[i].min());
                // System.out.println(moleculeSoFar);
            }
            //#endregion

            long runTime = System.currentTimeMillis() - startTime;
            System.out.println(moleculeSoFar + "," + String.valueOf(runTime/1000.0) + "," + String.valueOf(getMoleculePerplexity(moleculeSoFar)));
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void gpt_cp() {
        long startTime = System.currentTimeMillis();
        try {
            //#region Base initialization
            BaseModel.initialization();
            // Create variables to shorten access
            Solver cp = BaseModel.cp;
            CFG g = BaseModel.g;
            IntVar[] w = BaseModel.w;
            IntVar[] tokenWeights = BaseModel.tokenWeights;
            //#endregion
            
            //#region Constraints
            // Smiles Validity
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,8);
            GenConstraints.cycleParityConstraint(cp,w,g,1,8);

            // Other constraints
            GenConstraints.moleculeWeightConstraint(cp, w, tokenWeights, makeIntVar(cp, MIN_MOL_WEIGHT, MAX_MOL_WEIGHT), g);
            //#endregion

            //#region Solving
            String moleculeSoFar = "<s>";
            for (int i = 0; i < WORD_LENGTH; i++) {
                // Fills the rest with padding
                if (moleculeSoFar.endsWith("_")) {
                    for (int j = i; j < WORD_LENGTH; j++) {
                        moleculeSoFar += "_";
                        w[i].assign(g.tokenEncoder.get("_"));
                        cp.fixPoint();
                    }
                    break;
                }

                // Makes the request
                HashMap<Integer, Double> flattenedNLPScores = getModelProbabilities(g, moleculeSoFar);

                // Propagates constraints to determine current variable's values
                cp.fixPoint();

                // Removes all values that were filtered
                for (int j = 0; j < g.terminalCount(); j++) {
                    if (w[i].contains(j)) {
                        continue;
                    }
                    flattenedNLPScores.remove(j);
                }

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

                // Assign values
                w[i].assign(chosen);                
                moleculeSoFar += g.tokenDecoder.get(w[i].min());
                // System.out.println(moleculeSoFar);
            }
            //#endregion

            long runTime = System.currentTimeMillis() - startTime;
            System.out.println(moleculeSoFar + "," + String.valueOf(runTime/1000.0) + "," + String.valueOf(getMoleculePerplexity(moleculeSoFar)));
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void cpbp_no_back() {
        long startTime = System.currentTimeMillis();
        try {
            //#region Base initialization
            BaseModel.initialization();
            // Create variables to shorten access
            Solver cp = BaseModel.cp;
            CFG g = BaseModel.g;
            IntVar[] w = BaseModel.w;
            IntVar[] tokenWeights = BaseModel.tokenWeights;
            //#endregion
            
            //#region Constraints
            // Smiles Validity
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,8);
            GenConstraints.cycleParityConstraint(cp,w,g,1,8);

            // Other constraints
            GenConstraints.moleculeWeightConstraint(cp, w, tokenWeights, makeIntVar(cp, MIN_MOL_WEIGHT, MAX_MOL_WEIGHT), g);
            //#endregion
            
            //#region Solving
            int realLength = WORD_LENGTH;
            double[] chosenOdds = new double[WORD_LENGTH];
            String moleculeSoFar = "";
            // Generate the molecule
            for (int i = 0; i < WORD_LENGTH; i++) {
                // Pads the remainder of the molecule
                if (moleculeSoFar.endsWith("_")) {
                    realLength = i;
                    for (int j = i; j < WORD_LENGTH; j++) {
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
            }
            //#endregion
            
            long runTime = System.currentTimeMillis() - startTime;
            System.out.println(moleculeSoFar + "," + String.valueOf(runTime/1000.0) + "," + String.valueOf(getMoleculePerplexity(moleculeSoFar)));
        } catch (Exception e) {
            System.out.println(e);
        }
    }
    
    private static void cpbp_back() {
        long startTime = System.currentTimeMillis()/1000;
        try {
            //#region Base initialization
            BaseModel.initialization();
            // Create variables to shorten access
            Solver cp = BaseModel.cp;
            CFG g = BaseModel.g;
            IntVar[] w = BaseModel.w;
            IntVar[] tokenWeights = BaseModel.tokenWeights;
            //#endregion
            
            //#region Constraints
            // Smiles Validity
            GenConstraints.grammarConstraint(cp,w,g);
            GenConstraints.cycleCountingConstraint(cp,w,g,1,8);
            GenConstraints.cycleParityConstraint(cp,w,g,1,8);

            // Other constraints
            GenConstraints.moleculeWeightConstraint(cp, w, tokenWeights, makeIntVar(cp, MIN_MOL_WEIGHT, MAX_MOL_WEIGHT), g);
            //#endregion

            //#region Solving
            cp.setMode(PropaMode.SBP);
            LDSearch lds = makeLds(cp, maxMarginalStrengthBiasedWheelSelectVal(w));

            Vector<String> tokens = new Vector<>();
            lds.onSolution(() -> {
                for (int i = 0; i < w.length; i++) {
                    tokens.add(g.tokenDecoder.get(w[i].min()));
                }
            });

            SearchStatistics stats = lds.solve(stat -> stat.numberOfSolutions() == 1 || stat.timeElapsed() >= 15 * 60 * 1000);
            String molecule = String.join("", tokens);
            //#endregion
            
            System.out.println(molecule + "," + String.valueOf(stats.timeElapsed()/1000.0) + "," + String.valueOf(getMoleculePerplexity(molecule)));
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}

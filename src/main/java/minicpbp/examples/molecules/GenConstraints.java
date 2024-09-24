package minicpbp.examples.molecules;

import static minicpbp.cp.Factory.among;
import static minicpbp.cp.Factory.element;
import static minicpbp.cp.Factory.grammar;
import static minicpbp.cp.Factory.makeIntVar;
import static minicpbp.cp.Factory.makeIntVarArray;
import static minicpbp.cp.Factory.regular;
import static minicpbp.cp.Factory.sum;
import static minicpbp.cp.Factory.table;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.util.CFG;

public class GenConstraints {
    public static void grammarConstraint(Solver cp, IntVar[] w, CFG g) throws FileNotFoundException, IOException {
        cp.post(grammar(w,g));
    }

    public static int[] getCycleTokens(CFG g, int start, int end) {
        int nbCount = end - start + 1;
        int[] cycleTokens = new int[nbCount];
        for (int i = start; i < start + nbCount; i++) {
            String key = i < 10 ? Integer.toString(i) : '%' + Integer.toString(i);
            cycleTokens[i-start] = g.tokenEncoder.get(key);
        }
        return cycleTokens;
    }

    public static void cycleCountingConstraint(Solver cp, IntVar[] w, CFG g, int start, int end) {
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

    public static void cycleParityConstraint(Solver cp, IntVar[] w, CFG g, int start, int end) {
        int[] cycleTokens = getCycleTokens(g, start, end);
        for (int i = 0; i < cycleTokens.length; i++) {
            IntVar numberOccurrences = makeIntVar(cp, 0, 2);
            numberOccurrences.remove(1);
            cp.post(among(w, cycleTokens[i], numberOccurrences));
        }
    }

    public static void moleculeWeightConstraint(Solver cp, IntVar[] w, IntVar[] tokenWeights, IntVar weightTarget, CFG g) {
        int[] tokenToWeight = new int[g.terminalCount()];
        for (int i = 0; i < g.terminalCount(); i++) {
            String token = g.tokenDecoder.get(i);
            if (g.tokenWeight.containsKey(token)) {
                tokenToWeight[i] = g.tokenWeight.get(token);
            } else {
                tokenToWeight[i] = 0;
            }
        }
        for (int i = 0; i < w.length; i++) {
            cp.post(element(tokenToWeight, w[i], tokenWeights[i]));
        }
        cp.post(sum(tokenWeights, weightTarget));
    }

    public static void limitCycleConstraint(Solver cp, IntVar[] w, CFG g, int nCycles) {
        // A negative value disables the constraint
        if (nCycles == 0) { // Ensures no cycles
            cp.post(among(w, g.tokenEncoder.get("1"), makeIntVar(cp, 0,0)));
        } else if (nCycles > 0) {
            cp.post(among(w, g.tokenEncoder.get(Integer.toString(nCycles)), makeIntVar(cp, 2,2)));
            cp.post(among(w, g.tokenEncoder.get(Integer.toString(nCycles + 1)), makeIntVar(cp, 0,0)));
        }
    }

    public static void limitBranchConstraint(Solver cp, IntVar[] w, CFG g, int nBranches) {
        // A negative value disables the constraint
        if (nBranches >= 0) {
            cp.post(among(w, g.tokenEncoder.get("("), makeIntVar(cp, nBranches, nBranches)));
        }
    }

    public static IntVar lingoConstraint(
        Solver cp,
        IntVar[] w,
        CFG g,
        String filePath,
        int minValue,
        int maxValue
    ) throws FileNotFoundException, IOException {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            Vector<int[]> elementVector = new Vector<>();
            while (reader.ready()) {
                // Read, convert to right tokens, associate to weight * 100 rounded
                String[] line = reader.readLine().split(" ");
                String[] tokens = line[0].split(",");
                int weight = Math.round(Float.parseFloat(line[1]) * 100);

                elementVector.add(new int[]{
                    g.tokenEncoder.get(tokens[0]),
                    g.tokenEncoder.get(tokens[1]),
                    g.tokenEncoder.get(tokens[2]),
                    g.tokenEncoder.get(tokens[3]),
                    weight
                });
            }
            reader.close();
            int[][] elementTable = new int[elementVector.size()][5];
            elementTable = elementVector.toArray(elementTable);
            
            IntVar[] logPValues = makeIntVarArray(cp, w.length - 3, -800, 800);
            for (int i = 0; i < logPValues.length; i++) {
                cp.post(table(
                    new IntVar[] {w[i],w[i+1],w[i+2],w[i+3],logPValues[i]},
                    elementTable
                ));
            }

            IntVar logPEstimate = makeIntVar(cp, minValue, maxValue);
            cp.post(sum(logPValues, logPEstimate));
            return logPEstimate;
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        }
    }
}

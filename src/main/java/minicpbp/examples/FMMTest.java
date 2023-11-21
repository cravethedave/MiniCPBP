package minicpbp.examples;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.SearchStatistics;
import minicpbp.util.FMM;
import minicpbp.util.Procedure;

import static minicpbp.cp.BranchingScheme.firstFail;
import static minicpbp.cp.BranchingScheme.maxMarginal;
import static minicpbp.cp.BranchingScheme.and;
import static minicpbp.cp.Factory.*;

import java.util.function.Supplier;

public class FMMTest {
    public static void main(String[] args) {
        // Parameters of the problem
        int R = 7;
        FMM fmm = new FMM(2,2,2);
        fmm.printTensors();

        // Setting up the problem
        Solver cp = makeSolver();
        IntVar[] U = makeIntVarArray(cp, fmm.getASize()*R, -1, 1);
        IntVar[] V = makeIntVarArray(cp, fmm.getBSize()*R, -1, 1);
        IntVar[] W = makeIntVarArray(cp, fmm.getResultSize()*R, -1, 1);

        // Constraints
        //IntVar product(IntVar x, IntVar y)
        //IntVar sum(IntVar... x)
        //isEqual
        for (int c = 0; c < fmm.getResultSize(); c++) {
            for (int a = 0; a < fmm.getASize(); a++) {
                for (int b = 0; b < fmm.getBSize(); b++) {
                    IntVar[] sumIntVars = new IntVar[R];
                    for (int r = 0; r < R; r++) {
                        // table constraint on 4 vars and table containing all options
                        sumIntVars[r] = product(
                            product(
                                U[a * R + r],
                                V[b * R + r]
                            ),
                            W[c * R + r]
                        );
                    }
                    cp.post(sum(sumIntVars, fmm.getTensors(c,a,b)));
                }
            }
        }

        // concat the arrays and search
        IntVar[] searchIntVars = new IntVar[U.length + V.length + W.length];
        for (int i = 0; i < U.length; i++) {
            searchIntVars[i] = U[i];
        }
        int offset = U.length;
        for (int i = 0; i < V.length; i++) {
            searchIntVars[i+offset] = V[i];
        }
        offset += V.length;
        for (int i = 0; i < W.length; i++) {
            searchIntVars[i+offset] = W[i];
        }

        cp.setTraceBPFlag(true);
        DFSearch dfs = makeDfs(cp, maxMarginal(searchIntVars));

        dfs.onSolution(() -> {
            System.out.println("New Solution");
            if (!V[5].isBound()) System.out.println("This is wrong");
            printMatrix(U, fmm.getASize(), R);
            printMatrix(V, fmm.getBSize(), R);
            printMatrix(W, fmm.getResultSize(), R);
            System.out.println();
        });
        SearchStatistics stats = dfs.solve();
        System.out.println(stats);
    }

    public static void printMatrix(IntVar[] M, int rows, int columns) {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                int val = M[i * columns + j].max();
                if (val >= 0) System.out.print(" ");
                System.out.print(val);
                System.out.print(' ');
            }
            System.out.print('\n');
        }
        System.out.print('\n');
    }
}

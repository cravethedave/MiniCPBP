package minicpbp.util;

public class FMM {
    private int n, m, p, r, k1, k2;
    private int resultMatrixSize, matrixASize, matrixBSize;
    private int[][][] tensors;

    public FMM(int n, int m, int p, int r, int k1, int k2) {
        this.n = n;
        this.m = m;
        this.p = p;
        this.r = r;
        this.k1 = k1;
        this.k2 = k2;

        resultMatrixSize = n*p;
        matrixASize = n*m;
        matrixBSize = m*p;

        /**
         * These tensors contain the truth table for each position
         * of the resulting matrix.
         */
        tensors = new int[resultMatrixSize][matrixASize][matrixBSize];

        for (int c = 0; c < resultMatrixSize; c++) {
            // Which row of A is being multiplied to create this value
            int rowA = c / p; // between 0 and n-1
            // Which column of B is being multiplied to create this value
            int columnB = c % p; // between 0 and p-1

            for (int i = 0; i < m; i++) {
                int a = rowA * m + i;
                int b = i * p + columnB;
                tensors[c][a][b] = 1;
            }
        }
    }

    public void printTensors() {
        for (int i = 0; i < resultMatrixSize; i++) {
            for (int j = 0; j < matrixASize; j++) {
                for (int k = 0; k < matrixBSize; k++) {
                    System.out.print(tensors[i][j][k]);
                }
                System.out.print('\n');
            }
            System.out.print('\n');
        }
    }

    public void printValues() {
        System.out.println(String.format("%dx%dx%d r %d k1 %d k2 %d", n,m,p,r,k1,k2));
    }
    
    public int getN() {
        return n;
    }

    public int getM() {
        return m;
    }

    public int getP() {
        return p;
    }

    public int getR() {
        return r;
    }

    public int getK1() {
        return k1;
    }

    public int getK2() {
        return k2;
    }

    public int getASize() {
        return matrixASize;
    }

    public int getBSize() {
        return matrixBSize;
    }

    public int getResultSize() {
        return resultMatrixSize;
    }

    public int[][][] getTensors() {
        return tensors;
    }

    public int getTensors(int c,int a,int b) {
        return tensors[c][a][b];
    }
}

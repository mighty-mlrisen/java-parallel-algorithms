package org.example;


import java.io.FileWriter;
import java.io.IOException;
import mpi.*;
import java.util.*;

// последовательный

/*
public class Main {
    public static void main(String[] args) {
        int[] Ns = new int[]{100,500,1000};

        for (int N : Ns) {
            double[] A = new double[N * N];
            double[] B = new double[N * N];
            double[] C = new double[N * N];

            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    A[i * N + j] = 1.0;
                    B[i * N + j] = 1.0;
                }
            }

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    double sum = 0.0;
                    for (int k = 0; k < N; k++) {
                        sum += A[i * N + k] * B[k * N + j];
                    }
                    C[i * N + j] = sum;
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;

            try (FileWriter writer = new FileWriter("base_results.csv", true)) {
                writer.write("1," + N + "," + elapsed + "\n");
            } catch (IOException e) {
                System.err.println("CSV writing error: " + e.getMessage());
            }
            //System.out.println(Arrays.toString(C));
        }
    }
}



 */




// Блокирующий обмен (Send/Recv)


public class Main {
    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] Ns = new int[]{100, 500, 1000};
        //int[] Ns = new int[]{3};
        for (int N : Ns) {
            double[] fullA = null;
            double[] fullB = null;

            int baseRows = N / size;
            int remRows = N % size;
            int[] rowsPerProc = new int[size];
            int[] rowDispls = new int[size];
            int offR = 0;
            for (int p = 0; p < size; p++) {
                rowsPerProc[p] = baseRows + (p < remRows ? 1 : 0);
                rowDispls[p] = offR;
                offR += rowsPerProc[p];
            }

            int baseCols = N / size;
            int remCols = N % size;
            int[] colsPerProc = new int[size];
            int[] colDispls = new int[size];
            int offC = 0;
            for (int p = 0; p < size; p++) {
                colsPerProc[p] = baseCols + (p < remCols ? 1 : 0);
                colDispls[p] = offC;
                offC += colsPerProc[p];
            }

            int localRows = rowsPerProc[rank];
            int localCols = colsPerProc[rank];

            double[] localA = new double[localRows * N];
            double[] localB = new double[localCols * N];
            double[] localC = new double[localRows * N];

            if (rank == 0) {
                fullA = new double[N * N];
                fullB = new double[N * N];
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        fullA[i * N + j] = 1.0;
                        fullB[i * N + j] = 1.0;
                    }
                }
            }

            // Scatter rows of A
            double[] packedA = null;
            int[] sendCountsA = new int[size];
            int[] displsA = new int[size];
            if (rank == 0) {
                packedA = new double[N * N];
                int pos = 0;
                for (int p = 0; p < size; p++) {
                    for (int i = 0; i < rowsPerProc[p]; i++) {
                        System.arraycopy(fullA, (rowDispls[p] + i) * N, packedA, pos, N);
                        pos += N;
                    }
                }
            }
            int offSendA = 0;
            for (int p = 0; p < size; p++) {
                sendCountsA[p] = rowsPerProc[p] * N;
                displsA[p] = offSendA;
                offSendA += sendCountsA[p];
            }
            MPI.COMM_WORLD.Scatterv(packedA, 0, sendCountsA, displsA, MPI.DOUBLE,
                    localA, 0, localRows * N, MPI.DOUBLE, 0);

            // Scatter column stripes of B
            double[] packedB = null;
            int[] sendCountsB = new int[size];
            int[] displsB = new int[size];
            if (rank == 0) {
                packedB = new double[N * N];
                int posB = 0;
                for (int p = 0; p < size; p++) {
                    int cols = colsPerProc[p];
                    int startCol = colDispls[p];
                    for (int c = 0; c < cols; c++) {
                        System.arraycopy(fullB, (startCol + c) * N, packedB, posB, N);
                        posB += N;
                    }
                }
            }
            int offSendB = 0;
            for (int p = 0; p < size; p++) {
                sendCountsB[p] = colsPerProc[p] * N;
                displsB[p] = offSendB;
                offSendB += sendCountsB[p];
            }
            MPI.COMM_WORLD.Scatterv(packedB, 0, sendCountsB, displsB, MPI.DOUBLE,
                    localB, 0, localCols * N, MPI.DOUBLE, 0);

            MPI.COMM_WORLD.Barrier();
            long startCalc = System.currentTimeMillis();

            int maxCols = baseCols + 1;
            double[] currentB = new double[maxCols * N];
            double[] recvB = new double[maxCols * N];
            int curCols = localCols;
            System.arraycopy(localB, 0, currentB, 0, curCols * N);

            for (int step = 0; step < size; step++) {
                int owner = (rank - step + size) % size;
                int ownerCols = colsPerProc[owner];
                int ownerStart = colDispls[owner];

                for (int r = 0; r < localRows; r++) {
                    int aBase = r * N;
                    for (int c = 0; c < ownerCols; c++) {
                        double s = 0.0;
                        int bBase = c * N;
                        for (int k = 0; k < N; k++) {
                            s += localA[aBase + k] * currentB[bBase + k];
                        }
                        localC[r * N + (ownerStart + c)] += s;
                    }
                }

                if (size > 1 && step < size - 1) {
                    int next = (rank + 1) % size;
                    int prev = (rank - 1 + size) % size;
                    int recvCols = colsPerProc[(rank - step - 1 + size) % size];

                    MPI.COMM_WORLD.Sendrecv(currentB, 0, curCols * N, MPI.DOUBLE, next, 0,
                            recvB, 0, recvCols * N, MPI.DOUBLE, prev, 0);

                    double[] tmp = currentB;
                    currentB = recvB;
                    recvB = tmp;
                    curCols = recvCols;
                }
            }

            int[] gatherCounts = new int[size];
            int[] gatherDispls = new int[size];
            int offG = 0;
            for (int p = 0; p < size; p++) {
                gatherCounts[p] = rowsPerProc[p] * N;
                gatherDispls[p] = offG;
                offG += gatherCounts[p];
            }
            double[] fullC = (rank == 0) ? new double[N * N] : null;
            MPI.COMM_WORLD.Gatherv(localC, 0, localRows * N, MPI.DOUBLE,
                    fullC, 0, gatherCounts, gatherDispls, MPI.DOUBLE, 0);

            if (rank == 0) {
                long elapsedCalc = System.currentTimeMillis() - startCalc;
                try (FileWriter writer = new FileWriter("results_block.csv", true)) {
                    writer.write(size + "," + N + "," + elapsedCalc + "\n");

                } catch (IOException e) {
                    System.err.println("CSV writing error: " + e.getMessage());
                }
            }

        }

        MPI.Finalize();
    }
}






//Синхронный обмен (Ssend/Recv)

/*
public class Main {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] Ns = new int[]{100, 500, 1000};

        for (int N : Ns) {
            // Инициализируем массивы на всех узлах, чтобы избежать NPE в MPJ Express
            double[] fullA = new double[N * N];
            double[] fullB = new double[N * N];

            int baseRows = N / size;
            int remRows = N % size;
            int[] rowsPerProc = new int[size];
            int[] rowDispls = new int[size];
            int offR = 0;
            for (int p = 0; p < size; p++) {
                rowsPerProc[p] = baseRows + (p < remRows ? 1 : 0);
                rowDispls[p] = offR;
                offR += rowsPerProc[p];
            }

            int baseCols = N / size;
            int remCols = N % size;
            int[] colsPerProc = new int[size];
            int[] colDispls = new int[size];
            int offC = 0;
            for (int p = 0; p < size; p++) {
                colsPerProc[p] = baseCols + (p < remCols ? 1 : 0);
                colDispls[p] = offC;
                offC += colsPerProc[p];
            }

            int localRows = rowsPerProc[rank];
            int localCols = colsPerProc[rank];

            double[] localA = new double[localRows * N];
            double[] localB = new double[localCols * N];
            double[] localC = new double[localRows * N];

            if (rank == 0) {
                for (int i = 0; i < N * N; i++) {
                    fullA[i] = 1.0;
                    fullB[i] = 1.0;
                }
            }

            // Scatter A
            int[] sendCountsA = new int[size];
            int[] displsA = new int[size];
            for (int p = 0; p < size; p++) {
                sendCountsA[p] = rowsPerProc[p] * N;
                displsA[p] = rowDispls[p] * N;
            }
            MPI.COMM_WORLD.Scatterv(fullA, 0, sendCountsA, displsA, MPI.DOUBLE,
                    localA, 0, localRows * N, MPI.DOUBLE, 0);

            // Scatter B (подготовка упакованных колонок)
            double[] packedB = new double[N * N];
            if (rank == 0) {
                int posB = 0;
                for (int p = 0; p < size; p++) {
                    int cols = colsPerProc[p];
                    int startCol = colDispls[p];
                    for (int c = 0; c < cols; c++) {
                        System.arraycopy(fullB, (startCol + c) * N, packedB, posB, N);
                        posB += N;
                    }
                }
            }
            int[] sendCountsB = new int[size];
            int[] displsB = new int[size];
            int offSendB = 0;
            for (int p = 0; p < size; p++) {
                sendCountsB[p] = colsPerProc[p] * N;
                displsB[p] = offSendB;
                offSendB += sendCountsB[p];
            }
            MPI.COMM_WORLD.Scatterv(packedB, 0, sendCountsB, displsB, MPI.DOUBLE,
                    localB, 0, localCols * N, MPI.DOUBLE, 0);

            MPI.COMM_WORLD.Barrier();
            long startCalc = System.currentTimeMillis();

            int maxCols = (N / size) + 1;
            double[] currentB = new double[maxCols * N];
            double[] recvB = new double[maxCols * N];
            int curCols = localCols;
            System.arraycopy(localB, 0, currentB, 0, curCols * N);

            for (int step = 0; step < size; step++) {
                int owner = (rank - step + size) % size;
                int ownerCols = colsPerProc[owner];
                int ownerStart = colDispls[owner];

                // Вычисление
                for (int r = 0; r < localRows; r++) {
                    int aBase = r * N;
                    for (int c = 0; c < ownerCols; c++) {
                        double s = 0.0;
                        int bBase = c * N;
                        for (int k = 0; k < N; k++) {
                            s += localA[aBase + k] * currentB[bBase + k];
                        }
                        localC[r * N + (ownerStart + c)] += s;
                    }
                }

                // Передача через Ssend (Синхронный режим)
                if (size > 1 && step < size - 1) {
                    int next = (rank + 1) % size;
                    int prev = (rank - 1 + size) % size;
                    int recvCols = colsPerProc[(rank - step - 1 + size) % size];

                    // РАЗРЫВ ЦИКЛА ДЛЯ SSEND:
                    if (rank % 2 == 0) {
                        // Четные сначала отправляют
                        MPI.COMM_WORLD.Ssend(currentB, 0, curCols * N, MPI.DOUBLE, next, 0);
                        MPI.COMM_WORLD.Recv(recvB, 0, recvCols * N, MPI.DOUBLE, prev, 0);
                    } else {
                        // Нечетные сначала принимают
                        MPI.COMM_WORLD.Recv(recvB, 0, recvCols * N, MPI.DOUBLE, prev, 0);
                        MPI.COMM_WORLD.Ssend(currentB, 0, curCols * N, MPI.DOUBLE, next, 0);
                    }

                    System.arraycopy(recvB, 0, currentB, 0, recvCols * N);
                    curCols = recvCols;
                }
            }

            // Сбор данных
            double[] fullC = new double[N * N];
            MPI.COMM_WORLD.Gatherv(localC, 0, localRows * N, MPI.DOUBLE,
                    fullC, 0, sendCountsA, displsA, MPI.DOUBLE, 0);

            if (rank == 0) {
                long elapsedCalc = System.currentTimeMillis() - startCalc;
                System.out.println("Size: " + size + ", N: " + N + ", Time: " + elapsedCalc + "ms");
                try (FileWriter writer = new FileWriter("results_synchro.csv", true)) {
                    writer.write(size + "," + N + "," + elapsedCalc + "\n");
                } catch (IOException e) {
                    System.err.println("CSV writing error: " + e.getMessage());
                }
            }
        }

        MPI.Finalize();
    }
}

 */



//не работующий
/*
public class Main {
    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] Ns = new int[]{100, 500, 1000};

        for (int N : Ns) {
            double[] fullA = null;
            double[] fullB = null;

            int baseRows = N / size;
            int remRows = N % size;
            int[] rowsPerProc = new int[size];
            int[] rowDispls = new int[size];
            int offR = 0;
            for (int p = 0; p < size; p++) {
                rowsPerProc[p] = baseRows + (p < remRows ? 1 : 0);
                rowDispls[p] = offR;
                offR += rowsPerProc[p];
            }

            int baseCols = N / size;
            int remCols = N % size;
            int[] colsPerProc = new int[size];
            int[] colDispls = new int[size];
            int offC = 0;
            for (int p = 0; p < size; p++) {
                colsPerProc[p] = baseCols + (p < remCols ? 1 : 0);
                colDispls[p] = offC;
                offC += colsPerProc[p];
            }

            int localRows = rowsPerProc[rank];
            int localCols = colsPerProc[rank];

            double[] localA = new double[localRows * N];
            double[] localB = new double[localCols * N];
            double[] localC = new double[localRows * N];

            if (rank == 0) {
                fullA = new double[N * N];
                fullB = new double[N * N];
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        fullA[i * N + j] = 1.0;
                        fullB[i * N + j] = 1.0;
                    }
                }
            }

            double[] packedA = null;
            int[] sendCountsA = new int[size];
            int[] displsA = new int[size];
            if (rank == 0) {
                packedA = new double[N * N];
                int pos = 0;
                for (int p = 0; p < size; p++) {
                    for (int i = 0; i < rowsPerProc[p]; i++) {
                        System.arraycopy(fullA, (rowDispls[p] + i) * N, packedA, pos, N);
                        pos += N;
                    }
                }
            }
            int offSendA = 0;
            for (int p = 0; p < size; p++) {
                sendCountsA[p] = rowsPerProc[p] * N;
                displsA[p] = offSendA;
                offSendA += sendCountsA[p];
            }
            MPI.COMM_WORLD.Scatterv(packedA, 0, sendCountsA, displsA, MPI.DOUBLE,
                    localA, 0, localRows * N, MPI.DOUBLE, 0);

            double[] packedB = null;
            int[] sendCountsB = new int[size];
            int[] displsB = new int[size];
            if (rank == 0) {
                packedB = new double[N * N];
                int posB = 0;
                for (int p = 0; p < size; p++) {
                    int cols = colsPerProc[p];
                    int startCol = colDispls[p];
                    for (int c = 0; c < cols; c++) {
                        System.arraycopy(fullB, (startCol + c) * N, packedB, posB, N);
                        posB += N;
                    }
                }
            }
            int offSendB = 0;
            for (int p = 0; p < size; p++) {
                sendCountsB[p] = colsPerProc[p] * N;
                displsB[p] = offSendB;
                offSendB += sendCountsB[p];
            }
            MPI.COMM_WORLD.Scatterv(packedB, 0, sendCountsB, displsB, MPI.DOUBLE,
                    localB, 0, localCols * N, MPI.DOUBLE, 0);

            MPI.COMM_WORLD.Barrier();
            long startCalc = System.currentTimeMillis();

            int maxCols = baseCols + 1;
            double[] currentB = new double[maxCols * N];
            double[] recvB = new double[maxCols * N];
            int curCols = localCols;
            System.arraycopy(localB, 0, currentB, 0, curCols * N);

            for (int step = 0; step < size; step++) {
                int owner = (rank - step + size) % size;
                int ownerCols = colsPerProc[owner];
                int ownerStart = colDispls[owner];

                for (int r = 0; r < localRows; r++) {
                    int aBase = r * N;
                    for (int c = 0; c < ownerCols; c++) {
                        double s = 0.0;
                        int bBase = c * N;
                        for (int k = 0; k < N; k++) {
                            s += localA[aBase + k] * currentB[bBase + k];
                        }
                        localC[r * N + (ownerStart + c)] += s;
                    }
                }

                if (size > 1 && step < size - 1) {
                    int next = (rank + 1) % size;
                    int prev = (rank - 1 + size) % size;
                    int recvCols = colsPerProc[(rank - step - 1 + size) % size];


                    MPI.COMM_WORLD.Ssend(currentB, 0, curCols * N, MPI.DOUBLE, next, 0);


                    MPI.COMM_WORLD.Recv(recvB, 0, recvCols * N, MPI.DOUBLE, prev, 0);

                    double[] tmp = currentB;
                    currentB = recvB;
                    recvB = tmp;
                    curCols = recvCols;
                }
            }

            int[] gatherCounts = new int[size];
            int[] gatherDispls = new int[size];
            int offG = 0;
            for (int p = 0; p < size; p++) {
                gatherCounts[p] = rowsPerProc[p] * N;
                gatherDispls[p] = offG;
                offG += gatherCounts[p];
            }
            double[] fullC = (rank == 0) ? new double[N * N] : null;
            MPI.COMM_WORLD.Gatherv(localC, 0, localRows * N, MPI.DOUBLE,
                    fullC, 0, gatherCounts, gatherDispls, MPI.DOUBLE, 0);

            if (rank == 0) {
                long elapsedCalc = System.currentTimeMillis() - startCalc;
                try (FileWriter writer = new FileWriter("results_synchro.csv", true)) {
                    writer.write(size + "," + N + "," + elapsedCalc + "\n");
                } catch (IOException e) {
                    System.err.println("CSV writing error: " + e.getMessage());
                }
            }
        }

        MPI.Finalize();
    }
}



 */




//Обмен по готовности (Rsend/Recv)


/*
public class Main {
    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] Ns = new int[]{100, 500, 1000};

        for (int N : Ns) {
            double[] fullA = null;
            double[] fullB = null;

            int baseRows = N / size;
            int remRows = N % size;
            int[] rowsPerProc = new int[size];
            int[] rowDispls = new int[size];
            int offR = 0;
            for (int p = 0; p < size; p++) {
                rowsPerProc[p] = baseRows + (p < remRows ? 1 : 0);
                rowDispls[p] = offR;
                offR += rowsPerProc[p];
            }

            int baseCols = N / size;
            int remCols = N % size;
            int[] colsPerProc = new int[size];
            int[] colDispls = new int[size];
            int offC = 0;
            for (int p = 0; p < size; p++) {
                colsPerProc[p] = baseCols + (p < remCols ? 1 : 0);
                colDispls[p] = offC;
                offC += colsPerProc[p];
            }

            int localRows = rowsPerProc[rank];
            int localCols = colsPerProc[rank];

            double[] localA = new double[localRows * N];
            double[] localB = new double[localCols * N];
            double[] localC = new double[localRows * N];

            if (rank == 0) {
                fullA = new double[N * N];
                fullB = new double[N * N];
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        fullA[i * N + j] = 1.0;
                        fullB[i * N + j] = 1.0;
                    }
                }
            }

            double[] packedA = null;
            int[] sendCountsA = new int[size];
            int[] displsA = new int[size];
            if (rank == 0) {
                packedA = new double[N * N];
                int pos = 0;
                for (int p = 0; p < size; p++) {
                    for (int i = 0; i < rowsPerProc[p]; i++) {
                        System.arraycopy(fullA, (rowDispls[p] + i) * N, packedA, pos, N);
                        pos += N;
                    }
                }
            }
            int offSendA = 0;
            for (int p = 0; p < size; p++) {
                sendCountsA[p] = rowsPerProc[p] * N;
                displsA[p] = offSendA;
                offSendA += sendCountsA[p];
            }
            MPI.COMM_WORLD.Scatterv(packedA, 0, sendCountsA, displsA, MPI.DOUBLE,
                    localA, 0, localRows * N, MPI.DOUBLE, 0);

            double[] packedB = null;
            int[] sendCountsB = new int[size];
            int[] displsB = new int[size];
            if (rank == 0) {
                packedB = new double[N * N];
                int posB = 0;
                for (int p = 0; p < size; p++) {
                    int cols = colsPerProc[p];
                    int startCol = colDispls[p];
                    for (int c = 0; c < cols; c++) {
                        System.arraycopy(fullB, (startCol + c) * N, packedB, posB, N);
                        posB += N;
                    }
                }
            }
            int offSendB = 0;
            for (int p = 0; p < size; p++) {
                sendCountsB[p] = colsPerProc[p] * N;
                displsB[p] = offSendB;
                offSendB += sendCountsB[p];
            }
            MPI.COMM_WORLD.Scatterv(packedB, 0, sendCountsB, displsB, MPI.DOUBLE,
                    localB, 0, localCols * N, MPI.DOUBLE, 0);

            MPI.COMM_WORLD.Barrier();
            long startCalc = System.currentTimeMillis();

            int maxCols = baseCols + 1;
            double[] currentB = new double[maxCols * N];
            double[] recvB = new double[maxCols * N];
            int curCols = localCols;
            System.arraycopy(localB, 0, currentB, 0, curCols * N);

            for (int step = 0; step < size; step++) {
                int owner = (rank - step + size) % size;
                int ownerCols = colsPerProc[owner];
                int ownerStart = colDispls[owner];

                for (int r = 0; r < localRows; r++) {
                    int aBase = r * N;
                    for (int c = 0; c < ownerCols; c++) {
                        double s = 0.0;
                        int bBase = c * N;
                        for (int k = 0; k < N; k++) {
                            s += localA[aBase + k] * currentB[bBase + k];
                        }
                        localC[r * N + (ownerStart + c)] += s;
                    }
                }

                if (size > 1 && step < size - 1) {
                    int next = (rank + 1) % size;
                    int prev = (rank - 1 + size) % size;
                    int recvCols = colsPerProc[(rank - step - 1 + size) % size];

                    Request recvReq = MPI.COMM_WORLD.Irecv(recvB, 0, recvCols * N, MPI.DOUBLE, prev, 0);
                    MPI.COMM_WORLD.Rsend(currentB, 0, curCols * N, MPI.DOUBLE, next, 0);
                    recvReq.Wait();

                    double[] tmp = currentB;
                    currentB = recvB;
                    recvB = tmp;
                    curCols = recvCols;
                }
            }

            int[] gatherCounts = new int[size];
            int[] gatherDispls = new int[size];
            int offG = 0;
            for (int p = 0; p < size; p++) {
                gatherCounts[p] = rowsPerProc[p] * N;
                gatherDispls[p] = offG;
                offG += gatherCounts[p];
            }
            double[] fullC = (rank == 0) ? new double[N * N] : null;
            MPI.COMM_WORLD.Gatherv(localC, 0, localRows * N, MPI.DOUBLE,
                    fullC, 0, gatherCounts, gatherDispls, MPI.DOUBLE, 0);

            if (rank == 0) {
                long elapsedCalc = System.currentTimeMillis() - startCalc;
                try (FileWriter writer = new FileWriter("results_ready.csv", true)) {
                    writer.write(size + "," + N + "," + elapsedCalc + "\n");
                } catch (IOException e) {
                    System.err.println("CSV writing error: " + e.getMessage());
                }
            }
        }

        MPI.Finalize();
    }
}



 */


// Неблокирующий обмен (Isend/Irecv)

/*
public class Main {
    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] Ns = new int[]{100, 500, 1000};

        for (int N : Ns) {
            double[] fullA = null;
            double[] fullB = null;

            int baseRows = N / size;
            int remRows = N % size;
            int[] rowsPerProc = new int[size];
            int[] rowDispls = new int[size];
            int offR = 0;
            for (int p = 0; p < size; p++) {
                rowsPerProc[p] = baseRows + (p < remRows ? 1 : 0);
                rowDispls[p] = offR;
                offR += rowsPerProc[p];
            }

            int baseCols = N / size;
            int remCols = N % size;
            int[] colsPerProc = new int[size];
            int[] colDispls = new int[size];
            int offC = 0;
            for (int p = 0; p < size; p++) {
                colsPerProc[p] = baseCols + (p < remCols ? 1 : 0);
                colDispls[p] = offC;
                offC += colsPerProc[p];
            }

            int localRows = rowsPerProc[rank];
            int localCols = colsPerProc[rank];

            double[] localA = new double[localRows * N];
            double[] localB = new double[localCols * N];
            double[] localC = new double[localRows * N];

            if (rank == 0) {
                fullA = new double[N * N];
                fullB = new double[N * N];
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        fullA[i * N + j] = 1.0;
                        fullB[i * N + j] = 1.0;
                    }
                }
            }

            double[] packedA = null;
            int[] sendCountsA = new int[size];
            int[] displsA = new int[size];
            if (rank == 0) {
                packedA = new double[N * N];
                int pos = 0;
                for (int p = 0; p < size; p++) {
                    for (int i = 0; i < rowsPerProc[p]; i++) {
                        System.arraycopy(fullA, (rowDispls[p] + i) * N, packedA, pos, N);
                        pos += N;
                    }
                }
            }
            int offSendA = 0;
            for (int p = 0; p < size; p++) {
                sendCountsA[p] = rowsPerProc[p] * N;
                displsA[p] = offSendA;
                offSendA += sendCountsA[p];
            }
            MPI.COMM_WORLD.Scatterv(packedA, 0, sendCountsA, displsA, MPI.DOUBLE,
                    localA, 0, localRows * N, MPI.DOUBLE, 0);

            double[] packedB = null;
            int[] sendCountsB = new int[size];
            int[] displsB = new int[size];
            if (rank == 0) {
                packedB = new double[N * N];
                int posB = 0;
                for (int p = 0; p < size; p++) {
                    int cols = colsPerProc[p];
                    int startCol = colDispls[p];
                    for (int c = 0; c < cols; c++) {
                        System.arraycopy(fullB, (startCol + c) * N, packedB, posB, N);
                        posB += N;
                    }
                }
            }
            int offSendB = 0;
            for (int p = 0; p < size; p++) {
                sendCountsB[p] = colsPerProc[p] * N;
                displsB[p] = offSendB;
                offSendB += sendCountsB[p];
            }
            MPI.COMM_WORLD.Scatterv(packedB, 0, sendCountsB, displsB, MPI.DOUBLE,
                    localB, 0, localCols * N, MPI.DOUBLE, 0);

            MPI.COMM_WORLD.Barrier();
            long startCalc = System.currentTimeMillis();

            int maxCols = baseCols + 1;
            double[] currentB = new double[maxCols * N];
            double[] recvB = new double[maxCols * N];
            int curCols = localCols;
            System.arraycopy(localB, 0, currentB, 0, curCols * N);

            for (int step = 0; step < size; step++) {
                int owner = (rank - step + size) % size;
                int ownerCols = colsPerProc[owner];
                int ownerStart = colDispls[owner];

                for (int r = 0; r < localRows; r++) {
                    int aBase = r * N;
                    for (int c = 0; c < ownerCols; c++) {
                        double s = 0.0;
                        int bBase = c * N;
                        for (int k = 0; k < N; k++) {
                            s += localA[aBase + k] * currentB[bBase + k];
                        }
                        localC[r * N + (ownerStart + c)] += s;
                    }
                }

                if (size > 1 && step < size - 1) {
                    int next = (rank + 1) % size;
                    int prev = (rank - 1 + size) % size;
                    int recvCols = colsPerProc[(rank - step - 1 + size) % size];

                    Request sendReq = MPI.COMM_WORLD.Isend(currentB, 0, curCols * N, MPI.DOUBLE, next, 0);
                    Request recvReq = MPI.COMM_WORLD.Irecv(recvB, 0, recvCols * N, MPI.DOUBLE, prev, 0);
                    Request.Waitall(new Request[]{sendReq, recvReq});

                    double[] tmp = currentB;
                    currentB = recvB;
                    recvB = tmp;
                    curCols = recvCols;
                }
            }

            int[] gatherCounts = new int[size];
            int[] gatherDispls = new int[size];
            int offG = 0;
            for (int p = 0; p < size; p++) {
                gatherCounts[p] = rowsPerProc[p] * N;
                gatherDispls[p] = offG;
                offG += gatherCounts[p];
            }
            double[] fullC = (rank == 0) ? new double[N * N] : null;
            MPI.COMM_WORLD.Gatherv(localC, 0, localRows * N, MPI.DOUBLE,
                    fullC, 0, gatherCounts, gatherDispls, MPI.DOUBLE, 0);

            if (rank == 0) {
                long elapsedCalc = System.currentTimeMillis() - startCalc;
                try (FileWriter writer = new FileWriter("results_nonblock.csv", true)) {
                    writer.write(size + "," + N + "," + elapsedCalc + "\n");
                } catch (IOException e) {
                    System.err.println("CSV writing error: " + e.getMessage());
                }
            }
        }

        MPI.Finalize();
    }
}



 */



// Коллективы Broadcast/Reduce

//исправленный
/*
public class Main {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] Ns = new int[]{100, 500, 1000};

        for (int N : Ns) {
            // 1. Расчет распределения строк (кто сколько обрабатывает)
            int baseRows = N / size;
            int remRows = N % size;

            int[] sendCounts = new int[size];
            int[] displs = new int[size];
            int offset = 0;
            for (int i = 0; i < size; i++) {
                int rows = baseRows + (i < remRows ? 1 : 0);
                sendCounts[i] = rows * N;
                displs[i] = offset;
                offset += sendCounts[i];
            }

            int localRows = baseRows + (rank < remRows ? 1 : 0);

            // 2. Инициализация буферов (ВАЖНО: не оставляем null для MPJ Express)
            double[] fullA = new double[N * N];
            double[] fullB = new double[N * N];
            double[] fullC = new double[N * N];

            if (rank == 0) {
                for (int i = 0; i < N * N; i++) {
                    fullA[i] = 1.0;
                    fullB[i] = 1.0;
                }
            }

            double[] localA = new double[localRows * N];
            double[] localC = new double[localRows * N];

            // 3. Рассылка данных
            // Рассылаем матрицу B целиком всем (Broadcast)
            MPI.COMM_WORLD.Bcast(fullB, 0, N * N, MPI.DOUBLE, 0);

            // Рассылаем части матрицы A (Scatterv)
            MPI.COMM_WORLD.Scatterv(fullA, 0, sendCounts, displs, MPI.DOUBLE,
                    localA, 0, localRows * N, MPI.DOUBLE, 0);

            // 4. Вычисления
            MPI.COMM_WORLD.Barrier(); // Синхронизация перед замером
            long startCalc = System.currentTimeMillis();

            for (int i = 0; i < localRows; i++) {
                for (int j = 0; j < N; j++) {
                    double sum = 0;
                    int aIdx = i * N;
                    for (int k = 0; k < N; k++) {
                        sum += localA[aIdx + k] * fullB[k * N + j];
                    }
                    localC[i * N + j] = sum;
                }
            }

            long elapsedCalc = System.currentTimeMillis() - startCalc;

            // 5. Сбор результатов (Gatherv)
            MPI.COMM_WORLD.Gatherv(localC, 0, localRows * N, MPI.DOUBLE,
                    fullC, 0, sendCounts, displs, MPI.DOUBLE, 0);

            // 6. Запись результатов
            if (rank == 0) {
                System.out.println("Done N=" + N + " on " + size + " procs. Time: " + elapsedCalc + "ms");
                try (FileWriter writer = new FileWriter("results_broadcast_gatherv.csv", true)) {
                    writer.write(size + "," + N + "," + elapsedCalc + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        MPI.Finalize();
    }
}

 */
/*
public class Main {
    public static void main(String[] args) throws IOException {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] Ns = new int[]{100, 500, 1000};

        for (int N : Ns) {
            // Инициализация матриц на корневом процессе
            double[] fullA = null;
            double[] fullB = new double[N * N]; // будет разослана
            if (rank == 0) {
                fullA = new double[N * N];
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        fullA[i * N + j] = 1.0;
                        fullB[i * N + j] = 1.0;
                    }
                }
            }

            // Broadcast fullB всем процессам
            MPI.COMM_WORLD.Bcast(fullB, 0, N * N, MPI.DOUBLE, 0);

            int baseRows = N / size;
            int remRows = N % size;
            int localRows = baseRows + (rank < remRows ? 1 : 0);
            int rowStart = rank * baseRows + Math.min(rank, remRows);

            double[] localA = new double[localRows * N];
            double[] localC = new double[localRows * N];

            // Копируем соответствующие строки A
            if (rank == 0) {
                for (int p = 0; p < size; p++) {
                    int rows = baseRows + (p < remRows ? 1 : 0);
                    int startRow = p * baseRows + Math.min(p, remRows);
                    if (p == 0) {
                        System.arraycopy(fullA, 0, localA, 0, rows * N);
                    } else {
                        MPI.COMM_WORLD.Send(fullA, startRow * N, rows * N, MPI.DOUBLE, p, 0);
                    }
                }
            } else {
                MPI.COMM_WORLD.Recv(localA, 0, localRows * N, MPI.DOUBLE, 0, 0);
            }


            long startCalc = System.currentTimeMillis();
            for (int r = 0; r < localRows; r++) {
                int aBase = r * N;
                int cBase = r * N;
                for (int c = 0; c < N; c++) {
                    double sum = 0;
                    for (int k = 0; k < N; k++) {
                        sum += localA[aBase + k] * fullB[k * N + c];
                    }
                    localC[cBase + c] = sum;
                }
            }

            // Reduce для суммирования результатов на корневый процесс
            double[] fullC = null;
            if (rank == 0) fullC = new double[N * N];
            MPI.COMM_WORLD.Gather(localC, 0, localRows * N, MPI.DOUBLE, fullC, 0, localRows * N, MPI.DOUBLE, 0);

            long elapsedCalc = System.currentTimeMillis() - startCalc;

            if (rank == 0) {
                try (FileWriter writer = new FileWriter("results_broadcast_reduce.csv", true)) {
                    writer.write(size + "," + N + "," + elapsedCalc + "\n");
                }
            }
        }

        MPI.Finalize();
    }
}


 */

// c reduce
//рабочий
/*
public class Main {
    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] Ns = new int[]{100, 500, 1000};

        for (int N : Ns) {
            double[] fullA = null;
            double[] fullB = null;

            // Вычисляем, сколько строк получает каждый процесс
            int baseRows = N / size;
            int remRows = N % size;
            int[] rowsPerProc = new int[size];
            int[] rowDispls = new int[size];
            int offR = 0;
            for (int p = 0; p < size; p++) {
                rowsPerProc[p] = baseRows + (p < remRows ? 1 : 0);
                rowDispls[p] = offR;
                offR += rowsPerProc[p];
            }
            int localRows = rowsPerProc[rank];

            // Инициализация локальных массивов
            double[] localA = new double[localRows * N];
            double[] localC = new double[localRows * N];
            double[] localB = new double[N * N]; // полная матрица B для каждого процесса

            if (rank == 0) {
                fullA = new double[N * N];
                fullB = new double[N * N];
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        fullA[i * N + j] = 1.0;
                        fullB[i * N + j] = 1.0;
                    }
                }
            }

            // Scatterv для матрицы A
            double[] packedA = null;
            int[] sendCountsA = new int[size];
            int[] displsA = new int[size];
            if (rank == 0) {
                packedA = new double[N * N];
                int pos = 0;
                for (int p = 0; p < size; p++) {
                    for (int i = 0; i < rowsPerProc[p]; i++) {
                        System.arraycopy(fullA, (rowDispls[p] + i) * N, packedA, pos, N);
                        pos += N;
                    }
                }
            }
            int offSendA = 0;
            for (int p = 0; p < size; p++) {
                sendCountsA[p] = rowsPerProc[p] * N;
                displsA[p] = offSendA;
                offSendA += sendCountsA[p];
            }
            MPI.COMM_WORLD.Scatterv(packedA, 0, sendCountsA, displsA, MPI.DOUBLE,
                    localA, 0, localRows * N, MPI.DOUBLE, 0);

            // Broadcast полной матрицы B всем процессам
            if (rank == 0) {
                System.arraycopy(fullB, 0, localB, 0, N * N);
            }
            MPI.COMM_WORLD.Bcast(localB, 0, N * N, MPI.DOUBLE, 0);

            MPI.COMM_WORLD.Barrier();
            long startCalc = System.currentTimeMillis();

            // Локальное умножение
            for (int r = 0; r < localRows; r++) {
                for (int c = 0; c < N; c++) {
                    double sum = 0.0;
                    for (int k = 0; k < N; k++) {
                        sum += localA[r * N + k] * localB[k * N + c];
                    }
                    localC[r * N + c] = sum;
                }
            }

            // Reduce локальных результатов на корневой процесс
            double[] fullC = (rank == 0) ? new double[N * N] : new double[N * N];
            MPI.COMM_WORLD.Reduce(localC, 0, fullC, 0, localRows * N, MPI.DOUBLE, MPI.SUM, 0);

            if (rank == 0) {
                long elapsedCalc = System.currentTimeMillis() - startCalc;
                try (FileWriter writer = new FileWriter("results_bcast_reduce.csv", true)) {
                    writer.write(size + "," + N + "," + elapsedCalc + "\n");
                } catch (IOException e) {
                    System.err.println("CSV writing error: " + e.getMessage());
                }
            }
        }

        MPI.Finalize();
    }
}



 */



// Коллективы Scatter/Gather


/*
public class Main {
    public static void main(String[] args) {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int[] Ns = new int[]{100, 500, 1000};

        for (int N : Ns) {
            double[] fullA = null;
            double[] fullB = null;

            int baseRows = N / size;
            int remRows = N % size;
            int[] rowsPerProc = new int[size];
            int[] rowDispls = new int[size];
            int offR = 0;
            for (int p = 0; p < size; p++) {
                rowsPerProc[p] = baseRows + (p < remRows ? 1 : 0);
                rowDispls[p] = offR;
                offR += rowsPerProc[p];
            }

            int baseCols = N / size;
            int remCols = N % size;
            int[] colsPerProc = new int[size];
            int[] colDispls = new int[size];
            int offC = 0;
            for (int p = 0; p < size; p++) {
                colsPerProc[p] = baseCols + (p < remCols ? 1 : 0);
                colDispls[p] = offC;
                offC += colsPerProc[p];
            }

            int localRows = rowsPerProc[rank];
            int localCols = colsPerProc[rank];

            double[] localA = new double[localRows * N];
            double[] localB = new double[localCols * N];
            double[] localC = new double[localRows * N];

            if (rank == 0) {
                fullA = new double[N * N];
                fullB = new double[N * N];
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        fullA[i * N + j] = 1.0;
                        fullB[i * N + j] = 1.0;
                    }
                }
            }

            double[] packedA = null;
            int[] sendCountsA = new int[size];
            int[] displsA = new int[size];
            if (rank == 0) {
                packedA = new double[N * N];
                int pos = 0;
                for (int p = 0; p < size; p++) {
                    for (int i = 0; i < rowsPerProc[p]; i++) {
                        System.arraycopy(fullA, (rowDispls[p] + i) * N, packedA, pos, N);
                        pos += N;
                    }
                }
            }
            int offSendA = 0;
            for (int p = 0; p < size; p++) {
                sendCountsA[p] = rowsPerProc[p] * N;
                displsA[p] = offSendA;
                offSendA += sendCountsA[p];
            }
            MPI.COMM_WORLD.Scatterv(packedA, 0, sendCountsA, displsA, MPI.DOUBLE,
                    localA, 0, localRows * N, MPI.DOUBLE, 0);

            double[] packedB = null;
            int[] sendCountsB = new int[size];
            int[] displsB = new int[size];
            if (rank == 0) {
                packedB = new double[N * N];
                int posB = 0;
                for (int p = 0; p < size; p++) {
                    int cols = colsPerProc[p];
                    int startCol = colDispls[p];
                    for (int c = 0; c < cols; c++) {
                        System.arraycopy(fullB, (startCol + c) * N, packedB, posB, N);
                        posB += N;
                    }
                }
            }
            int offSendB = 0;
            for (int p = 0; p < size; p++) {
                sendCountsB[p] = colsPerProc[p] * N;
                displsB[p] = offSendB;
                offSendB += sendCountsB[p];
            }
            MPI.COMM_WORLD.Scatterv(packedB, 0, sendCountsB, displsB, MPI.DOUBLE,
                    localB, 0, localCols * N, MPI.DOUBLE, 0);

            MPI.COMM_WORLD.Barrier();
            long startCalc = System.currentTimeMillis();

            int maxCols = baseCols + 1;
            double[] currentB = new double[maxCols * N];
            double[] recvB = new double[maxCols * N];
            int curCols = localCols;
            System.arraycopy(localB, 0, currentB, 0, curCols * N);

            for (int step = 0; step < size; step++) {
                int owner = (rank - step + size) % size;
                int ownerCols = colsPerProc[owner];
                int ownerStart = colDispls[owner];

                for (int r = 0; r < localRows; r++) {
                    int aBase = r * N;
                    for (int c = 0; c < ownerCols; c++) {
                        double s = 0.0;
                        int bBase = c * N;
                        for (int k = 0; k < N; k++) {
                            s += localA[aBase + k] * currentB[bBase + k];
                        }
                        localC[r * N + (ownerStart + c)] += s;
                    }
                }

                if (size > 1 && step < size - 1) {
                    int next = (rank + 1) % size;
                    int prev = (rank - 1 + size) % size;
                    int recvCols = colsPerProc[(rank - step - 1 + size) % size];

                    MPI.COMM_WORLD.Sendrecv(currentB, 0, curCols * N, MPI.DOUBLE, next, 0,
                            recvB, 0, recvCols * N, MPI.DOUBLE, prev, 0);

                    double[] tmp = currentB;
                    currentB = recvB;
                    recvB = tmp;
                    curCols = recvCols;
                }
            }

            int[] gatherCounts = new int[size];
            int[] gatherDispls = new int[size];
            int offG = 0;
            for (int p = 0; p < size; p++) {
                gatherCounts[p] = rowsPerProc[p] * N;
                gatherDispls[p] = offG;
                offG += gatherCounts[p];
            }
            double[] fullC = (rank == 0) ? new double[N * N] : null;
            MPI.COMM_WORLD.Gatherv(localC, 0, localRows * N, MPI.DOUBLE,
                    fullC, 0, gatherCounts, gatherDispls, MPI.DOUBLE, 0);

            if (rank == 0) {
                long elapsedCalc = System.currentTimeMillis() - startCalc;
                try (FileWriter writer = new FileWriter("results_scatter.csv", true)) {
                    writer.write(size + "," + N + "," + elapsedCalc + "\n");
                } catch (IOException e) {
                    System.err.println("CSV writing error: " + e.getMessage());
                }
            }
        }

        MPI.Finalize();
    }
}


 */




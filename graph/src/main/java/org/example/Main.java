package org.example;

import mpi.*;
import java.io.*;
import java.util.*;

public class Main {
    static final int INF = 1_000_000;

    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int n = 2001;
        int[] flatAdj = new int[n * n];
        if (rank == 0) {
            for (int i = 0; i < n - 1; i++) {
                flatAdj[i * n + i + 1] = 1;
                flatAdj[(i + 1) * n + i] = 1;
            }
        }
        long start = System.nanoTime();
        MPI.COMM_WORLD.Bcast(flatAdj, 0, n * n, MPI.INT, 0);

        int startV = rank * (n / size) + Math.min(rank, n % size);
        int endV = startV + (n / size) + (rank < n % size ? 1 : 0);
        int localCount = endV - startV;
        int[] myV = new int[localCount];
        for (int i = 0; i < localCount; i++) myV[i] = startV + i;

        int[] counts = new int[size], displs = new int[size];
        for (int i = 0; i < size; i++) {
            counts[i] = n / size + ((i < n % size) ? 1 : 0);
            if (i > 0) displs[i] = displs[i - 1] + counts[i - 1];
        }

        int[] localEcc = new int[localCount];
        for (int i = 0; i < localCount; i++) localEcc[i] = ecc(myV[i], n, flatAdj);

        int[] allEcc = new int[n];
        MPI.COMM_WORLD.Gatherv(localEcc, 0, localCount, MPI.INT, allEcc, 0, counts, displs, MPI.INT, 0);

        if (rank == 0) {
            long timeMs = (System.nanoTime() - start) / 1_000_000;
            int minEcc = Arrays.stream(allEcc).min().getAsInt();
            List<Integer> centers = new ArrayList<>();
            for (int i = 0; i < n; i++) if (allEcc[i] == minEcc) centers.add(i);
            System.out.println("Center: " + centers);

            try (FileWriter fw = new FileWriter("time.csv", true)) {
                fw.write(size + "," + timeMs + "\n");
            }
        }
        MPI.Finalize();
    }

    static int ecc(int s, int n, int[] a) {
        int[] d = new int[n];
        Arrays.fill(d, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        d[s] = 0; q.add(s);
        while (!q.isEmpty()) {
            int u = q.poll(), row = u * n;
            for (int v = 0; v < n; v++)
                if (a[row + v] != 0 && d[v] == -1) {
                    d[v] = d[u] + 1;
                    q.add(v);
                }
        }
        int e = 0;
        for (int x : d) { if (x == -1) return INF; if (x > e) e = x; }
        return e;
    }
}
package org.example;

import mpi.*;

import java.io.FileWriter;
import java.io.IOException;

public class Main {

    // 0) Последовательная программа

    public static void main(String[] args){
        int N = 100;
        if (args.length > 0) {
            try {
                N = Integer.parseInt(args[args.length - 1]);
            } catch (NumberFormatException e) {
                System.out.println("N incorrect or not specified. Using default value: " + N);
            }
        }
        double[] a = null;
        double[] b = null;
        long startTime = 0, endTime = 0;

        a = new double[N];
        b = new double[N];
        for (int i = 0; i < N; i++) {
            a[i] = 1.0;
            b[i] = 1.0;
        }
        startTime = System.currentTimeMillis();

        double total = 0.0;
        for (int i = 0; i < N; i++){
            total += a[i]*b[i];
        }

        endTime = System.currentTimeMillis();
        long elapsed = endTime - startTime;

        try (FileWriter writer = new FileWriter("base_results.csv", true)) {
            writer.write( "1," + N + "," + elapsed + "\n");
        } catch (IOException e) {
            System.err.println("CSV writing error: " + e.getMessage());
        }
    }



    // 1.1) Стандартный обмен
//    public static void main(String[] args){
//        MPI.Init(args);
//
//        int rank = MPI.COMM_WORLD.Rank();
//        int size = MPI.COMM_WORLD.Size();
//
//        int N = 100;
//        if (args.length > 0) {
//            try {
//                N = Integer.parseInt(args[args.length - 1]);
//            } catch (NumberFormatException e) {
//                if (rank == 0) System.out.println("N incorrect or not specified. Using default value: " + N);
//            }
//        }
//        double[] a = null;
//        double[] b = null;
//        long startTime = 0, endTime = 0;
//
//        if (rank == 0) {
//            a = new double[N];
//            b = new double[N];
//            for (int i = 0; i < N; i++) {
//                a[i] = 1.0;
//                b[i] = 1.0;
//            }
//            startTime = System.currentTimeMillis();
//        }
//
//        int base = N / size;
//        int remainder = N % size;
//        int localSize = (rank < remainder) ? base + 1 : base;
//
//        int[] offsets = new int[size];
//        int offset = 0;
//        for (int i = 0; i < size; i++) {
//            offsets[i] = offset;
//            offset += (i < remainder) ? base + 1 : base;
//        }
//
//        double[] localA = new double[localSize];
//        double[] localB = new double[localSize];
//
//        if (rank == 0) {
//            for (int i = 1; i < size; i++) {
//                int len = (i < remainder) ? base + 1 : base;
//                MPI.COMM_WORLD.Send(a, offsets[i], len, MPI.DOUBLE, i, 0);
//            }
//            System.arraycopy(a, 0, localA, 0, localSize);
//        } else {
//            MPI.COMM_WORLD.Recv(localA, 0, localSize, MPI.DOUBLE, 0, 0);
//        }
//
//        if (rank == 0) {
//            MPI.COMM_WORLD.Send(b, 0, N, MPI.DOUBLE, 1, 1);
//        } else {
//            b = new double[N];
//            MPI.COMM_WORLD.Recv(b, 0, N, MPI.DOUBLE, rank - 1, 1);
//            if (rank != size - 1) {
//                MPI.COMM_WORLD.Send(b, 0, N, MPI.DOUBLE, (rank + 1) % size, 1);
//            }
//        }
//
//        double localSum = 0.0;
//        for (int i = 0; i < localSize; i++) {
//            localSum += localA[i] * b[offsets[rank] + i];
//        }
//
//        if (rank == 0) {
//            double total = localSum;
//            double[] buf = new double[1];
//            for (int i = 1; i < size; i++) {
//                MPI.COMM_WORLD.Recv(buf, 0, 1, MPI.DOUBLE, i, 2);
//                total += buf[0];
//            }
//            endTime = System.currentTimeMillis();
//            long elapsed = endTime - startTime;
//
//            try (FileWriter writer = new FileWriter("results_block.csv", true)) {
//                writer.write(size + "," + N + "," + elapsed + "\n");
//            } catch (IOException e) {
//                System.err.println("CSV writing error: " + e.getMessage());
//            }
//        } else {
//            double[] sendBuf = { localSum };
//            MPI.COMM_WORLD.Send(sendBuf, 0, 1, MPI.DOUBLE, 0, 2);
//        }
//
//        MPI.Finalize();
//    }


    // 1.2) Синхронизированный обмен
//    public static void main(String[] args) {
//        MPI.Init(args);
//
//        int rank = MPI.COMM_WORLD.Rank();
//        int size = MPI.COMM_WORLD.Size();
//
//        int N = 100;
//        if (args.length > 0) {
//            try {
//                N = Integer.parseInt(args[args.length - 1]);
//            } catch (NumberFormatException e) {
//                if (rank == 0) System.out.println("N incorrect or not specified. Using default value: " + N);
//            }
//        }
//
//        double[] a = null;
//        double[] b = null;
//        long startTime = 0, endTime = 0;
//
//        if (rank == 0) {
//            a = new double[N];
//            b = new double[N];
//            for (int i = 0; i < N; i++) {
//                a[i] = 1.0;
//                b[i] = 1.0;
//            }
//            startTime = System.currentTimeMillis();
//        }
//
//        int base = N / size;
//        int remainder = N % size;
//        int localSize = (rank < remainder) ? base + 1 : base;
//
//        int[] offsets = new int[size];
//        int offset = 0;
//        for (int i = 0; i < size; i++) {
//            offsets[i] = offset;
//            offset += (i < remainder) ? base + 1 : base;
//        }
//
//        double[] localA = new double[localSize];
//        double[] localB = new double[localSize];
//
//        if (rank == 0) {
//            for (int i = 1; i < size; i++) {
//                int len = (i < remainder) ? base + 1 : base;
//                MPI.COMM_WORLD.Ssend(a, offsets[i], len, MPI.DOUBLE, i, 0);
//            }
//            System.arraycopy(a, 0, localA, 0, localSize);
//        } else {
//            MPI.COMM_WORLD.Recv(localA, 0, localSize, MPI.DOUBLE, 0, 0);
//        }
//
//        if (rank == 0) {
//            MPI.COMM_WORLD.Ssend(b, 0, N, MPI.DOUBLE, 1, 1);
//        } else {
//            b = new double[N];
//            MPI.COMM_WORLD.Recv(b, 0, N, MPI.DOUBLE, rank - 1, 1);
//            if (rank != size - 1) {
//                MPI.COMM_WORLD.Ssend(b, 0, N, MPI.DOUBLE, (rank + 1) % size, 1);
//            }
//        }
//
//        double localSum = 0.0;
//        for (int i = 0; i < localSize; i++) {
//            localSum += localA[i] * b[offsets[rank] + i];
//        }
//
//        if (rank == 0) {
//            double total = localSum;
//            double[] buf = new double[1];
//            for (int i = 1; i < size; i++) {
//                MPI.COMM_WORLD.Recv(buf, 0, 1, MPI.DOUBLE, i, 2);
//                total += buf[0];
//            }
//            endTime = System.currentTimeMillis();
//            long elapsed = endTime - startTime;
//
//            try (FileWriter writer = new FileWriter("results_synchro.csv", true)) {
//                writer.write(size + "," + N + "," + elapsed + "\n");
//            } catch (IOException e) {
//                System.err.println("CSV writing error: " + e.getMessage());
//            }
//        } else {
//            double[] sendBuf = { localSum };
//            MPI.COMM_WORLD.Ssend(sendBuf, 0, 1, MPI.DOUBLE, 0, 2);
//        }
//
//        MPI.Finalize();
//    }


    // 1.3) Обмен по готовности
//    public static void main(String[] args) {
//        MPI.Init(args);
//
//        int rank = MPI.COMM_WORLD.Rank();
//        int size = MPI.COMM_WORLD.Size();
//
//        int N = 100;
//        if (args.length > 0) {
//            try {
//                N = Integer.parseInt(args[args.length - 1]);
//            } catch (NumberFormatException e) {
//                if (rank == 0)
//                    System.out.println("N incorrect or not specified. Using default: " + N);
//            }
//        }
//
//        double[] a = null;
//        double[] b = null;
//        long startTime = 0, endTime = 0;
//
//        if (rank == 0) {
//            a = new double[N];
//            b = new double[N];
//            for (int i = 0; i < N; i++) {
//                a[i] = 1.0;
//                b[i] = 1.0;
//            }
//            startTime = System.currentTimeMillis();
//        }
//
//        int base = N / size;
//        int remainder = N % size;
//        int localSize = (rank < remainder) ? base + 1 : base;
//
//        int[] offsets = new int[size];
//        int offset = 0;
//        for (int i = 0; i < size; i++) {
//            offsets[i] = offset;
//            offset += (i < remainder) ? base + 1 : base;
//        }
//
//        double[] localA = new double[localSize];
//
//        if (rank == 0) {
//            System.arraycopy(a, 0, localA, 0, localSize);
//            for (int i = 1; i < size; i++) {
//                int len = (i < remainder) ? base + 1 : base;
//                MPI.COMM_WORLD.Rsend(a, offsets[i], len, MPI.DOUBLE, i, 0);
//            }
//        } else {
//            MPI.COMM_WORLD.Recv(localA, 0, localSize, MPI.DOUBLE, 0, 0);
//        }
//
//        if (rank == 0) {
//            if (size > 1) {
//                MPI.COMM_WORLD.Rsend(b, 0, N, MPI.DOUBLE, 1, 1);
//            }
//        } else {
//            b = new double[N];
//            MPI.COMM_WORLD.Recv(b, 0, N, MPI.DOUBLE, rank - 1, 1);
//            if (rank < size - 1) {
//                MPI.COMM_WORLD.Rsend(b, 0, N, MPI.DOUBLE, rank + 1, 1);
//            }
//        }
//
//        double localSum = 0.0;
//        for (int i = 0; i < localSize; i++) {
//            localSum += localA[i] * b[offsets[rank] + i];
//        }
//
//        if (rank == 0) {
//            double total = localSum;
//            double[] buf = new double[1];
//            for (int i = 1; i < size; i++) {
//                MPI.COMM_WORLD.Recv(buf, 0, 1, MPI.DOUBLE, i, 2);
//                total += buf[0];
//            }
//            endTime = System.currentTimeMillis();
//            long elapsed = endTime - startTime;
//
//            try (FileWriter writer = new FileWriter("results_ready.csv", true)) {
//                writer.write(size + "," + N + "," + elapsed + "\n");
//            } catch (IOException e) {
//                System.err.println("CSV writing error: " + e.getMessage());
//            }
//
//        } else {
//            double[] sendBuf = { localSum };
//            MPI.COMM_WORLD.Rsend(sendBuf, 0, 1, MPI.DOUBLE, 0, 2);
//        }
//
//        MPI.Finalize();
//    }


    // 1.4) Неблокирующие обмены
//    public static void main(String[] args){
//        MPI.Init(args);
//
//        int rank = MPI.COMM_WORLD.Rank();
//        int size = MPI.COMM_WORLD.Size();
//
//        int N = 100;
//        if (args.length > 0) {
//            try {
//                N = Integer.parseInt(args[args.length - 1]);
//            } catch (NumberFormatException e) {
//                if (rank == 0)
//                    System.out.println("N incorrect or not specified. Using default value: " + N);
//            }
//        }
//
//        double[] a = null;
//        double[] localA;
//        double[] b = new double[N];
//        double[] localB = new double[N];
//        long startTime = 0, endTime = 0;
//
//        if (rank == 0) {
//            a = new double[N];
//            for (int i = 0; i < N; i++) {
//                a[i] = 1.0;
//                b[i] = 1.0;
//            }
//            startTime = System.currentTimeMillis();
//        }
//
//        int base = N / size;
//        int remainder = N % size;
//        int localSize = (rank < remainder) ? base + 1 : base;
//
//        int[] offsets = new int[size];
//        int offset = 0;
//        for (int i = 0; i < size; i++) {
//            offsets[i] = offset;
//            offset += (i < remainder) ? base + 1 : base;
//        }
//
//        localA = new double[localSize];
//
//        if (rank == 0) {
//            System.arraycopy(a, 0, localA, 0, localSize);
//            Request[] requests = new Request[size - 1];
//            for (int i = 1; i < size; i++) {
//                int len = (i < remainder) ? base + 1 : base;
//                requests[i - 1] = MPI.COMM_WORLD.Isend(a, offsets[i], len, MPI.DOUBLE, i, 0);
//            }
//            Request.Waitall(requests);
//        } else {
//            MPI.COMM_WORLD.Irecv(localA, 0, localSize, MPI.DOUBLE, 0, 0).Wait();
//        }
//
//        if (rank == 0) {
//            if (size > 1) {
//                MPI.COMM_WORLD.Isend(b, 0, N, MPI.DOUBLE, 1, 1).Wait();
//            }
//        } else {
//            MPI.COMM_WORLD.Irecv(b, 0, N, MPI.DOUBLE, rank - 1, 1).Wait();
//            if (rank < size - 1) {
//                MPI.COMM_WORLD.Isend(b, 0, N, MPI.DOUBLE, rank + 1, 1).Wait();
//            }
//        }
//
//        double localSum = 0.0;
//        for (int i = 0; i < localSize; i++) {
//            localSum += localA[i] * b[offsets[rank] + i];
//        }
//
//        if (rank == 0) {
//            double total = localSum;
//            double[] buf = new double[1];
//            for (int i = 1; i < size; i++) {
//                MPI.COMM_WORLD.Irecv(buf, 0, 1, MPI.DOUBLE, i, 2).Wait();
//                total += buf[0];
//            }
//            endTime = System.currentTimeMillis();
//            long elapsed = endTime - startTime;
//
//            // Запись в CSV
//            try (FileWriter writer = new FileWriter("results_nonblock.csv", true)) {
//                writer.write(size + "," + N + "," + elapsed + "\n");
//            } catch (IOException e) {
//                System.err.println("CSV writing error: " + e.getMessage());
//            }
//
//        } else {
//            double[] sendBuf = { localSum };
//            MPI.COMM_WORLD.Isend(sendBuf, 0, 1, MPI.DOUBLE, 0, 2).Wait();
//        }
//
//        MPI.Finalize();
//    }


    // 2.1) Коллективный обмен Broadcast/Reduce
//    public static void main(String[] args){
//        MPI.Init(args);
//
//        int rank = MPI.COMM_WORLD.Rank();
//        int size = MPI.COMM_WORLD.Size();
//
//        int N = 100;
//        if (args.length > 0) {
//            try {
//                N = Integer.parseInt(args[args.length - 1]);
//            } catch (NumberFormatException e) {
//                if (rank == 0)
//                    System.out.println("N incorrect or not specified. Using default value: " + N);
//            }
//        }
//
//        double[] a = new double[N];
//        double[] b = new double[N];
//        long startTime = 0, endTime = 0;
//
//        if (rank == 0) {
//            for (int i = 0; i < N; i++) {
//                a[i] = 1.0;
//                b[i] = 1.0;
//            }
//            startTime = System.currentTimeMillis();
//        }
//
//        MPI.COMM_WORLD.Bcast(a, 0, N, MPI.DOUBLE, 0);
//
//        if (rank == 0) {
//            MPI.COMM_WORLD.Send(b, 0, N, MPI.DOUBLE, 1, 1);
//        } else {
//            MPI.COMM_WORLD.Recv(b, 0, N, MPI.DOUBLE, rank - 1, 1);
//            if (rank != size - 1) {
//                MPI.COMM_WORLD.Send(b, 0, N, MPI.DOUBLE, rank + 1, 1);
//            }
//        }
//
//        int base = N / size;
//        int remainder = N % size;
//        int localStart = (rank < remainder) ? rank * (base + 1) : rank * base + remainder;
//        int localSize = (rank < remainder) ? base + 1 : base;
//
//        double localSum = 0.0;
//        for (int i = 0; i < localSize; i++) {
//            localSum += a[localStart + i] * b[localStart + i];
//        }
//
//        double[] totalSum = new double[1];
//        MPI.COMM_WORLD.Reduce(new double[]{localSum}, 0, totalSum, 0, 1, MPI.DOUBLE, MPI.SUM, 0);
//
//        if (rank == 0) {
//            endTime = System.currentTimeMillis();
//            long elapsed = endTime - startTime;
//
//            try (FileWriter writer = new FileWriter("results_broadcast.csv", true)) {
//                writer.write(size + "," + N + "," + elapsed + "\n");
//            } catch (IOException e) {
//                System.err.println("CSV writing error: " + e.getMessage());
//            }
//        }
//
//        MPI.Finalize();
//    }

/*
    // 2.2) Коллективный обмен Scatter/Gather
    public static void main(String[] args){
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int N = 100;
        if (args.length > 0) {
            try {
                N = Integer.parseInt(args[args.length - 1]);
            } catch (NumberFormatException e) {
                if (rank == 0)
                    System.out.println("N incorrect or not specified. Using default value: " + N);
            }
        }

        double[] a = new double[N];
        double[] b = new double[N];
        long startTime = 0, endTime = 0;

        if (rank == 0) {
            for (int i = 0; i < N; i++) {
                a[i] = 1.0;
                b[i] = 1.0;
            }
            startTime = System.currentTimeMillis();
        }

        int base = N / size;
        int remainder = N % size;
        int[] sendCounts = new int[size];
        int[] displs = new int[size];
        int offset = 0;
        for (int i = 0; i < size; i++) {
            sendCounts[i] = (i < remainder) ? base + 1 : base;
            displs[i] = offset;
            offset += sendCounts[i];
        }

        int localSize = sendCounts[rank];
        double[] localA = new double[localSize];

        MPI.COMM_WORLD.Scatterv(a, 0, sendCounts, displs, MPI.DOUBLE, localA, 0, localSize, MPI.DOUBLE, 0);

        if (rank == 0) {
            MPI.COMM_WORLD.Send(b, 0, N, MPI.DOUBLE, 1, 1);
        } else {
            MPI.COMM_WORLD.Recv(b, 0, N, MPI.DOUBLE, rank - 1, 1);
            if (rank != size - 1) {
                MPI.COMM_WORLD.Send(b, 0, N, MPI.DOUBLE, rank + 1, 1);
            }
        }

        int localStart = displs[rank];
        double localSum = 0.0;
        for (int i = 0; i < localSize; i++) {
            localSum += localA[i] * b[localStart + i];
        }

        double[] allSums = new double[size];

        MPI.COMM_WORLD.Gather(new double[]{localSum}, 0, 1, MPI.DOUBLE, allSums, 0, 1, MPI.DOUBLE, 0);

        if (rank == 0) {
            double total = 0.0;
            for (double s : allSums) {
                total += s;
            }

            endTime = System.currentTimeMillis();
            long elapsed = endTime - startTime;

            try (FileWriter writer = new FileWriter("results_scatter.csv", true)) {
                writer.write(size + "," + N + "," + elapsed + "\n");
            } catch (IOException e) {
                System.err.println("CSV writing error: " + e.getMessage());
            }
        }

        MPI.Finalize();
    }

 */

}


package org.example;


/*
import mpi.*;

public class HelloWorld {
    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        System.out.println("Hello from rank " + rank + " of " + size);
        MPI.Finalize();
    }
}

*/

import mpi.*;

public class HelloWorld {
    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        final int TAG = 0;

        int[] message = new int[1];
        message[0] = rank;

        if ((rank % 2) == 0) {
            if (rank + 1 < size) {
                MPI.COMM_WORLD.Send(message, 0, 1, MPI.INT, rank + 1, TAG);
                System.out.println("rank " + rank + " sent " + message[0] + " to " + (rank + 1));
            }
        } else {
            MPI.COMM_WORLD.Recv(message, 0, 1, MPI.INT, rank - 1, TAG);
            System.out.println("rank " + rank + " received: " + message[0] + " from " + (rank - 1));
        }

        MPI.Finalize();
    }
}




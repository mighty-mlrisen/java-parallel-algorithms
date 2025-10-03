package org.example;

import mpi.*;

public class Main {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        int TAG = 0;

        int[] buf = new int[]{rank};
        int[] recvBuf = new int[1];

/*
        switch (Integer.valueOf(rank)) {
            case Integer r when r == 0 -> {
                MPI.COMM_WORLD.Sendrecv(
                        buf, 0, 1, MPI.INT, r + 1, TAG,
                        recvBuf, 0, 1, MPI.INT, size - 1, TAG
                );
                buf[0] += recvBuf[0];

                System.out.println("Процесс 0 — сумма: " + buf[0]);
            }
            case Integer r when r == (size - 1) -> {
                MPI.COMM_WORLD.Recv(recvBuf, 0, 1, MPI.INT, r -1, TAG);
                System.out.println("Процесс " + rank + " получил: " + recvBuf[0]);
                buf[0] += recvBuf[0];
                int s = 0;
                for (int i = 0; i <= 5; i++) {
                    s += i;
                }
                System.out.println(s);
                MPI.COMM_WORLD.Send(buf, 0, 1, MPI.INT, 0, TAG);
            }
            case Integer r -> {
                MPI.COMM_WORLD.Recv(recvBuf, 0, 1, MPI.INT, r - 1, TAG);
                System.out.println("Процесс " + rank + " получил: " + recvBuf[0]);
                buf[0] += recvBuf[0];
                int s = 0;
                for (int i = 0; i <= 5; i++) {
                    s += i;
                }
                System.out.println(s);
                MPI.COMM_WORLD.Send(buf, 0, 1, MPI.INT, r + 1, TAG);
            }
        }

        MPI.Finalize();
*/


        switch (Integer.valueOf(rank)) {
            case Integer r when r == 0 -> {
                MPI.COMM_WORLD.Isend(buf, 0, 1, MPI.INT, r + 1, TAG);
                Request recvReq = MPI.COMM_WORLD.Irecv(recvBuf, 0, 1, MPI.INT, size - 1, TAG);
                recvReq.Wait();
                buf[0] += recvBuf[0];
                System.out.println("Сумма рангов на процессе " + r + ": " + buf[0]);
            }
            case Integer r when r == (size - 1) -> {
                Request recvReq = MPI.COMM_WORLD.Irecv(recvBuf, 0, 1, MPI.INT, r - 1, TAG);
                int s = 0;
                for (int i = 0; i <= 5; i++) {
                    s += i;
                }
                System.out.println(s);
                recvReq.Wait();
                System.out.println("Процесс " + r + " получил сообщение: " + recvBuf[0]);
                buf[0] += recvBuf[0];
                MPI.COMM_WORLD.Isend(buf, 0, 1, MPI.INT, 0, TAG);
            }
            case Integer r -> {
                Request recvReq = MPI.COMM_WORLD.Irecv(recvBuf, 0, 1, MPI.INT, r - 1, TAG);
                int s = 0;
                for (int i = 0; i <= 5; i++) {
                    s += i;
                }
                System.out.println(s);
                recvReq.Wait();
                System.out.println("Процесс " + r + " получил сообщение: " + recvBuf[0]);
                buf[0] += recvBuf[0];
                MPI.COMM_WORLD.Isend(buf, 0, 1, MPI.INT, r + 1, TAG);
            }
        }

        MPI.Finalize();


    }
}














/*
package org.example;

import mpi.*;

public class Main {
    public static void main(String[] args) {
        MPI.Init(args);

        int myrank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        int TAG = 0;

        int[] buf = new int[1];
        buf[0] = myrank;

        int[] received_message = new int[1];


        if (myrank == 0) {
            MPI.COMM_WORLD.Sendrecv(buf, 0, 1, MPI.INT, myrank + 1, TAG, received_message, 0, 1, MPI.INT, size - 1, TAG);
            buf[0] += received_message[0];
            System.out.println("Rank sum from process " + myrank + ": " + buf[0]);
        } else if (myrank == (size - 1)) {
            MPI.COMM_WORLD.Recv(received_message, 0, 1, MPI.INT, myrank - 1, TAG);
            System.out.println("Process " + myrank + " received message: " + received_message[0]);
            buf[0] += received_message[0];
            MPI.COMM_WORLD.Send(buf, 0, 1, MPI.INT, 0, TAG);
        } else {
            MPI.COMM_WORLD.Recv(received_message, 0, 1, MPI.INT, myrank - 1, TAG);
            System.out.println("Process " + myrank + " received message: " + received_message[0]);
            buf[0] += received_message[0];
            MPI.COMM_WORLD.Send(buf, 0, 1, MPI.INT, myrank + 1, TAG);
        }
        MPI.Finalize();


        if (myrank == 0) {
            MPI.COMM_WORLD.Isend(buf, 0, 1, MPI.INT, myrank + 1, TAG);
            Request recvReq = MPI.COMM_WORLD.Irecv(received_message, 0, 1, MPI.INT, size - 1, TAG);
            recvReq.Wait();
            buf[0] += received_message[0];
            System.out.println("Rank sum from process " + myrank + ": " + buf[0]);

        } else if (myrank == (size - 1)) {
            Request recvReq = MPI.COMM_WORLD.Irecv(received_message, 0, 1, MPI.INT, myrank - 1, TAG);
            recvReq.Wait();
            System.out.println("Process " + myrank + " received message: " + received_message[0]);
            buf[0] += received_message[0];
            MPI.COMM_WORLD.Isend(buf, 0, 1, MPI.INT, 0, TAG);
        } else {
            Request recvReq = MPI.COMM_WORLD.Irecv(received_message, 0, 1, MPI.INT, myrank - 1, TAG);
            recvReq.Wait();
            System.out.println("Process " + myrank + " received message: " + received_message[0]);
            buf[0] += received_message[0];
            MPI.COMM_WORLD.Isend(buf, 0, 1, MPI.INT, myrank + 1, TAG);
        }
        MPI.Finalize();
    }
}
long time = System.currentTimeMillis();

*/
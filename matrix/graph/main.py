
import pandas as pd
import matplotlib.pyplot as plt

files = {
    'Block send/recv': 'results_block.csv',
    'Synchronous send (Ssend)': 'results_synchro.csv',
    'Ready send (Rsend)': 'results_ready.csv',
    'Non-blocking (Isend/Irecv)': 'results_nonblock.csv',
    'Broadcast/Reduce': 'results_broadcast.csv',
    'Scatter/Gather': 'results_scatter.csv'
}

df_base = pd.read_csv('base_results.csv', header=None, names=['processes', 'N', 'time_ms'])
df_base = df_base.sort_values(by='N')

plt.figure(figsize=(10, 6))

plt.plot(df_base['N'], df_base['time_ms'], marker='o', linestyle='-', label='Base (single process)')

for label, filename in files.items():
    df = pd.read_csv(filename, header=None, names=['processes', 'N', 'time_ms'])
    df = df.sort_values(by='N')

    plt.plot(df['N'], df['time_ms'], marker='o', linestyle='--', label=label)

plt.xlabel('matrix size (N)')
plt.ylabel('Execution time (ms)')
plt.title('Performance comparison of different MPI communication methods')
plt.legend()
plt.grid(True, which="both", ls="--", linewidth=0.5)
plt.tight_layout()
plt.show()

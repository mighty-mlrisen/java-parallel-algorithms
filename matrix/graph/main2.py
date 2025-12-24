import pandas as pd
import matplotlib.pyplot as plt

df = pd.read_csv('results.csv', header=None, names=['processes', 'N', 'time_ms'])
df_base = pd.read_csv('base_results.csv', header=None, names=['processes', 'N', 'time_ms'])

plt.figure(figsize=(10, 6))
subset = df_base[df_base['processes'] == 1]
plt.plot(subset['N'], subset['time_ms'], marker='o', label=f'Single process')
for p in sorted(df['processes'].unique()):
    subset = df[df['processes'] == p]
    subset = subset.sort_values(by='N')
    plt.plot(subset['N'], subset['time_ms'], marker='o', label=f'{p} process(es)')

plt.xlabel('matrix size (N)')
plt.ylabel('Execution time (ms)')
plt.title('matrix dot product time performance')
plt.legend()
plt.grid(True, which="both", ls="--", linewidth=0.5)
plt.tight_layout()
plt.show()
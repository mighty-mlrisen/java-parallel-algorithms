
import csv
import matplotlib.pyplot as plt
from matplotlib.ticker import MaxNLocator

x, y = [], []
with open("time.csv") as f:
    reader = csv.DictReader(f)
    for row in reader:
        x.append(int(row["procs"]))
        y.append(int(row["time_ms"]))

plt.plot(x, y, marker='o')
plt.xlabel("Число процессов")
plt.ylabel("Время выполнения (мс)")
plt.title("Зависимость времени выполнения от числа процессов")
plt.grid(True)
plt.gca().xaxis.set_major_locator(MaxNLocator(integer=True))
plt.show()

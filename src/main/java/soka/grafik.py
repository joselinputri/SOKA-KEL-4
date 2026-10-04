"""
Buat grafik dari CSV hasil simulasi.
Jalankan dari folder project setelah DatasetMetricExtractor selesai:
    pip install pandas matplotlib
    python grafik.py
Output: hasil/grafik_skala.png, hasil/grafik_waiting_kategori.png, hasil/grafik_beban.png
"""
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

ALGO = ["RASA", "RR", "FCFS", "MINMIN", "MAXMIN"]
WARNA = {"RASA": "#d62728", "RR": "#7f7f7f", "FCFS": "#1f77b4", "MINMIN": "#2ca02c", "MAXMIN": "#ff7f0e"}

# 1. Pola terhadap jumlah task
sk = pd.read_csv("hasil/skala_task.csv")
fig, ax = plt.subplots(2, 2, figsize=(13, 9))
panel = [
    ("makespan_mean", "Makespan (detik)", False),
    ("gap_pct_mean", "Gap ke batas bawah (%) - skala log", True),
    ("di_mean", "Degree of Imbalance - skala log", True),
    ("avg_waiting_mean", "Avg waiting time (detik)", False),
]
for a, (kol, judul, log) in zip(ax.flat, panel):
    for al in ALGO:
        d = sk[sk.algo == al]
        a.plot(d.jumlah_task, d[kol], marker="o", label=al, color=WARNA[al], lw=2.4 if al == "RASA" else 1.4)
    a.set_title(judul)
    a.set_xlabel("Jumlah task")
    if log:
        a.set_yscale("log")
    a.grid(alpha=.3)
ax[0][0].legend()
runs = int(sk.runs.iloc[0])
fig.suptitle(f"Pengaruh jumlah task (data sintetis, rata-rata {runs} run)")
fig.tight_layout()
fig.savefig("hasil/grafik_skala.png", dpi=150)

# 2. Waiting time per kategori
w = pd.read_csv("hasil/waiting_per_kategori.csv")
kat = ["Small", "Medium", "Large", "Extra Large"]
fig, a = plt.subplots(figsize=(10, 5.5))
lebar = 0.16
for i, al in enumerate(ALGO):
    d = w[w.algo == al].set_index("kategori").reindex(kat)
    a.bar([x + i * lebar for x in range(len(kat))], d.avg_waiting_time, lebar, label=al, color=WARNA[al])
a.set_xticks([x + 2 * lebar for x in range(len(kat))])
a.set_xticklabels(kat)
a.set_ylabel("Avg waiting time (detik)")
a.set_title("Waiting time per kategori task (600 task, urutan asli)")
a.legend()
a.grid(axis="y", alpha=.3)
fig.tight_layout()
fig.savefig("hasil/grafik_waiting_kategori.png", dpi=150)

# 3. Beban ringan / seimbang / berat
b = pd.read_csv("hasil/beban_dataset.csv")
sken = ["ringan", "seimbang_asli", "berat"]
fig, ax = plt.subplots(1, 2, figsize=(13, 5))
for a, (kol, std, judul) in zip(ax, [("makespan_mean", "makespan_std", "Makespan (detik)"),
                                     ("di_mean", "di_std", "Degree of Imbalance")]):
    for i, al in enumerate(ALGO):
        d = b[b.algo == al].set_index("skenario").reindex(sken)
        a.bar([x + i * lebar for x in range(len(sken))], d[kol], lebar, yerr=d[std],
              label=al, color=WARNA[al], capsize=2)
    a.set_xticks([x + 2 * lebar for x in range(len(sken))])
    a.set_xticklabels(sken)
    a.set_title(judul)
    a.grid(axis="y", alpha=.3)
ax[1].set_yscale("log")
ax[0].legend()
fig.suptitle("Pengaruh jenis beban dataset (600 task, rata-rata ± std)")
fig.tight_layout()
fig.savefig("hasil/grafik_beban.png", dpi=150)
print("Grafik tersimpan di folder hasil/")
# LAPORAN LENGKAP SIMULASI TASK SCHEDULING PADA CLOUD DATA CENTER
## Algoritma Resource Aware Scheduling Algorithm (RASA) Berbasis CloudSim

**Kelompok 4:**
1. **Thio Billy Amansyah** — 5027231007
2. **Putri Joselina Silitonga** — 5027241116
3. **Muhammad Khairul Yahya** — 5027241092
4. **Nabilah Anindya Paramesti** — 5027241006
5. **Zahra Hafizhah** — 5027241121

---

## Daftar Isi
1. [Ringkasan Eksekutif](#1-ringkasan-eksekutif)
2. [Latar Belakang dan Tujuan](#2-latar-belakang-dan-tujuan)
3. [Karakteristik Workload dan Dataset Pengujian](#3-karakteristik-workload-dan-dataset-pengujian)
4. [Arsitektur Cloud Data Center](#4-arsitektur-cloud-data-center)
5. [Prinsip Kerja Algoritma RASA & Baseline](#5-prinsip-kerja-algoritma-rasa--baseline)
6. [Fungsi Objektif dan Metrik Evaluasi](#6-fungsi-objektif-dan-metrik-evaluasi)
7. [Hasil Eksperimen & Analisis Pembahasan](#7-hasil-eksperimen--analisis-pembahasan)
   - [7.1 Perbandingan Performa 5 Algoritma (600 Cloudlet)](#71-perbandingan-performa-5-algoritma-600-cloudlet)
   - [7.2 Analisis Peningkatan Performa RASA terhadap Algoritma Lain](#72-analisis-peningkatan-performa-rasa-terhadap-algoritma-lain)
   - [7.3 Analisis Keseimbangan Beban Kerja per Virtual Machine](#73-analisis-keseimbangan-beban-kerja-per-virtual-machine)
   - [7.4 Analisis Trade-off Makespan vs Waiting Time (Fairness)](#74-analisis-trade-off-makespan-vs-waiting-time-fairness)
   - [7.5 Uji Robustness: Sweep 30 Urutan Task Acak (Mean ± Std Dev)](#75-uji-robustness-sweep-30-urutan-task-acak-mean--std-dev)
   - [7.6 Uji Skalabilitas Sistem (100, 200, 400, 600 Task)](#76-uji-skalabilitas-sistem-100-200-400-600-task)
8. [Arsitektur Kode & Struktur Proyek](#8-arsitektur-kode--struktur-proyek)
9. [Panduan Menjalankan Simulasi (Replikasi)](#9-panduan-menjalankan-simulasi-replikasi)
10. [Kesimpulan](#10-kesimpulan)

---

## 1. Ringkasan Eksekutif

Proyek ini mengimplementasikan, menguji, dan mengevaluasi algoritma **Resource Aware Scheduling Algorithm (RASA)** pada lingkungan simulasi cloud data center heterogen menggunakan toolkit **CloudSim 3.0.3**. Pengujian dilakukan menggunakan dataset nyata **Google Cloud Jobs (GoCJ)** dengan beban 600 task independen (*Independent Bag-of-Tasks*) pada topologi data center yang terdiri atas 6 Host fisik (3 Host Tipe A high-spec, 3 Host Tipe B low-spec) dan 15 Virtual Machine (9 VM Tipe 1 @ 1000 MIPS, 6 VM Tipe 2 @ 500 MIPS).

Kinerja algoritma RASA dibandingkan secara komprehensif terhadap empat algoritma standar:
1. **Round-Robin (RR)** (Algoritma bawaan DatacenterBroker CloudSim)
2. **First-Come First-Served (FCFS)** dengan penugasan *earliest completion time*
3. **Min-Min Scheduling**
4. **Max-Min Scheduling**

### Temuan Utama:
- **Makespan & Utilisasi:** RASA menyelesaikan 600 task dalam waktu **6.918,10 detik**, hanya berjarak **0,82%** dari batas bawah teoretis ideal (*theoretical lower bound* = 6.861,75 detik), dengan utilisasi resource mencapai **99,11%**. RASA mengungguli Round-Robin sebesar **48,50%** (speedup 1,94×), FCFS sebesar **7,57%**, dan Min-Min sebesar **5,87%**.
- **Keseimbangan Beban (Load Balancing):** RASA menghasilkan *Degree of Imbalance* (DI) yang sangat rendah (**0,0251**), mencerminkan beban kerja yang sangat merata antar-VM dan melampaui Round-Robin (DI = 1,1684) serta Min-Min (DI = 0,2200).
- **Mitigasi Starvation & Fairness:** RASA berhasil mengeliminasi fenomena kelaparan (*starvation*) pada task kecil yang dialami oleh Max-Min, menurunkan *average waiting time* sebesar **12,99%** dan *average turnaround time* sebesar **12,55%** dibandingkan Max-Min, sekaligus mempertahankan makespan yang hampir setara.

---

## 2. Latar Belakang dan Tujuan

Penjadwalan task (*task scheduling*) merupakan salah satu tantangan paling kritis pada pengelolaan sumber daya komputasi awan (*cloud computing*). Penyedia layanan cloud harus mampu memetakan ribuan hingga jutaan task heterogen ke mesin-mesin virtual dengan kapasitas komputasi yang bervariasi tanpa menimbulkan waktu tunda berlebih (*makespan*) dan ketimpangan utilisasi antar-mesin (*load imbalance*).

Algoritma heuristik klasik memiliki kelemahan inheren:
- **Min-Min** memprioritaskan task kecil, menyebabkan task berukuran besar menunggu lama dan mesin cepat berpotensi *overloaded* sementara mesin lambat menganggur.
- **Max-Min** memprioritaskan task besar, efektif menurunkan makespan global namun menyebabkan task-task kecil mengalami *starvation* (waktu tunggu melonjak).
- **Round-Robin** membagi task secara sekuensial tanpa memperhitungkan heterogenitas kapasitas CPU (MIPS), menghasilkan pemborosan resource yang masif.

**Tujuan Penelitian:**
1. Mengembangkan broker kustom CloudSim yang mengintegrasikan algoritma RASA (Parsa & Entezari-Maleki, 2009).
2. Mengevaluasi performa RASA pada lingkungan heterogen berbasis workload GoCJ nyata.
3. Membuktikan bahwa pergantian strategi (*alternating heuristic*) Min-Min dan Max-Min pada RASA menghasilkan ekuilibrium optimal antara minimalisasi makespan, pemerataan beban kerja (*load balance*), dan keadilan antrian (*fairness*).

---

## 3. Karakteristik Workload dan Dataset Pengujian

### 3.1 Jenis Workload
- **Tipe Task:** *Independent Bag-of-Tasks* (tidak memiliki dependensi urutan/precedence DAG).
- **Sifat Eksekusi:** *Computation-intensive*, *non-preemptive* (task yang berjalan tidak dapat disela/dipindahkan hingga tuntas).
- **Waktu Kedatangan:** *Batch mode* / *offline* (seluruh 600 task telah tersedia di awal antrian broker pada $t = 0$).
- **Parameter Cloudlet:** Single-threaded (1 PE/vCPU), ukuran file input 300 KB, ukuran file output 300 KB.

### 3.2 Dataset Pengujian (Google Cloud Jobs - GoCJ)
Dataset sintetis yang diturunkan dari karakteristik beban nyata *Google Cluster Trace* (Hussain & Aleem, MDPI Data Journal 2018). Setiap record merepresentasikan panjang job dalam satuan Million Instructions (MI).

Pengelompokan 600 cloudlet:

| Kategori | Rentang Panjang (MI) | Proporsi Dataset | Jumlah Task | Karakteristik Beban |
|:---|:---:|:---:|:---:|:---|
| **Small** | 3.000 – 47.999 | 9,7% | 58 | Task ringan / interaktif |
| **Medium** | 48.000 – 99.999 | 47,5% | 285 | Beban standar / batch moderat |
| **Large** | 100.000 – 449.999 | 35,8% | 215 | Beban komputasi intensif |
| **Extra Large** | 450.000 – 999.999 | 7,0% | 42 | Big data analytics / heavy batch |
| **Total** | **3.000 – 999.999** | **100%** | **600** | **Total MI = 82.341.000 MI** |

> **Catatan Analisis Workload:**
> Distribusi dataset didominasi oleh kategori Medium dan Large (mencapai **83,3%** dari total task). Kondisi ini memberikan stress test nyata terhadap algoritma penjadwalan, karena jika algoritma salah mendistribusikan task berukuran ratusan ribu MI, ketimpangan waktu selesai antar-VM akan menjadi sangat tinggi.

---

## 4. Arsitektur Cloud Data Center

Model infrastruktur dibangun menggunakan hierarki fisik dan virtual yang heterogen:

```
                            [ Cloud Data Center: 1 Unit ]
                                          |
                +-------------------------+-------------------------+
                |                                                   |
      [ 3x Host Tipe A (High-Spec) ]                      [ 3x Host Tipe B (Low-Spec) ]
      - 8 PE @ 4000 MIPS (Total 32.000 MIPS)              - 4 PE @ 2000 MIPS (Total 8.000 MIPS)
      - 16 GB RAM, 10 Gbps BW, 1 TB Disk                  - 8 GB RAM, 5 Gbps BW, 1 TB Disk
      - Alokasi: 3 VM Tipe 1 per Host                     - Alokasi: 2 VM Tipe 2 per Host
                |                                                   |
                v                                                   v
      [ 9x VM Tipe 1 (ID 0–8) ]                           [ 6x VM Tipe 2 (ID 9–14) ]
      - 1 vCPU @ 1000 MIPS                                - 1 vCPU @ 500 MIPS
      - 2 GB RAM, 1 Gbps BW                               - 1 GB RAM, 500 Mbps BW
                \                                                   /
                 +------------------------+------------------------+
                                          |
                                   Total Kapasitas:
                           15 VM | 12.000 Total MIPS
```

### 4.1 Spesifikasi Host Fisik

| Spesifikasi | Host Tipe A (High-Spec, 3 Unit) | Host Tipe B (Low-Spec, 3 Unit) |
|:---|:---:|:---:|
| **Host ID** | Host 0, Host 1, Host 2 | Host 3, Host 4, Host 5 |
| **MIPS per Core** | 4.000 MIPS | 2.000 MIPS |
| **Jumlah Core (PE)** | 8 Core | 4 Core |
| **Kapasitas Komputasi** | 32.000 MIPS / host | 8.000 MIPS / host |
| **RAM** | 16 GB (16.384 MB) | 8 GB (8.192 MB) |
| **Bandwidth** | 10 Gbps (10.000 Mbps) | 5 Gbps (5.000 Mbps) |
| **Storage** | 1 TB (1.000.000 MB) | 1 TB (500.000 MB) |
| **VM Scheduler** | `VmSchedulerTimeShared` | `VmSchedulerTimeShared` |

### 4.2 Spesifikasi Virtual Machine (VM)

| Spesifikasi | VM Tipe 1 (9 Unit) | VM Tipe 2 (6 Unit) |
|:---|:---:|:---:|
| **VM ID** | VM 0 – VM 8 | VM 9 – VM 14 |
| **Host Penempatan** | 3 VM per Host Tipe A | 2 VM per Host Tipe B |
| **vCPU (PE)** | 1 vCPU | 1 vCPU |
| **MIPS** | **1.000 MIPS** | **500 MIPS** |
| **RAM** | 2 GB (2.048 MB) | 1 GB (1.024 MB) |
| **Bandwidth** | 1.000 Mbps | 500 Mbps |
| **Image Size** | 10.000 MB | 5.000 MB |
| **Cloudlet Scheduler** | `CloudletSchedulerSpaceShared` | `CloudletSchedulerSpaceShared` |

### 4.3 Kebijakan Alokasi VM Custom (`VmAllocationPolicyByType`)
Untuk memastikan VM Tipe 1 ditempatkan secara deterministik pada Host Tipe A (3 VM/Host) dan VM Tipe 2 pada Host Tipe B (2 VM/Host), diimplementasikan kelas `VmAllocationPolicyByType` yang mengecek kompatibilitas spesifikasi sebelum mapping dilakukan.

---

## 5. Prinsip Kerja Algoritma RASA & Baseline

### 5.1 Algoritma RASA (Resource Aware Scheduling Algorithm)
RASA menggabungkan keunggulan Makespan dari **Max-Min** dengan sifat perlindungan antrian kecil dari **Min-Min**.

#### Tahapan Algoritma:
1. **Matriks Expected Completion Time (ECT):**
   Untuk setiap task $i$ dan VM $j$, hitung waktu eksekusi teoretis:
   $$\text{ECT}[i][j] = \frac{\text{Length}_i \text{ (MI)}}{\text{MIPS}_j}$$
2. **Completion Time Aktual:**
   $$\text{CT}[i][j] = \text{ECT}[i][j] + \text{AvailableTime}[j]$$
   di mana $\text{AvailableTime}[j]$ adalah waktu selesai task terakhir yang telah dijadwalkan pada VM $j$.
3. **Penentuan Strategi Ronde Pertama:**
   Ditentukan berdasarkan **paritas jumlah VM (resource)**:
   $$\text{Ronde Pertama} = \begin{cases} \text{Min-Min}, & \text{jika } |VM| \text{ ganjil} \\ \text{Max-Min}, & \text{jika } |VM| \text{ genap} \end{cases}$$
   Karena pada penelitian ini jumlah VM = **15 (ganjil)**, maka ronde pertama dimulai dengan **Min-Min**, lalu ronde ke-2 **Max-Min**, ronde ke-3 **Min-Min**, dan seterusnya berselang-seling.
4. **Logika Pemilihan Task per Ronde:**
   - **Ronde Min-Min:** Cari pasangan $(\text{task}_i, \text{VM}_j)$ yang menghasilkan nilai $\text{CT}[i][j]$ **minimum global** di antara seluruh sisa task.
   - **Ronde Max-Min:** Untuk setiap task yang tersisa, tentukan VM terbaiknya ($\min_j \text{CT}[i][j]$), lalu pilih task yang memiliki completion time terbaik **paling besar** (mewakili task besar).
5. **Update Status:** Task terpilih diikat (*bound*) ke VM tujuan, $\text{AvailableTime}[\text{VM}]$ diperbarui menjadi nilai $\text{CT}$, dan task dihapus dari daftar kandidat hingga seluruh 600 task terjadwal.

```mermaid
flowchart TD
    A[Mulai Penjadwalan RASA] --> B[Hitung Matriks ECT task x VM]
    B --> C{Jumlah VM Ganjil?}
    C -- Ya (15 VM) --> D[Set Strategi Awal = Min-Min]
    C -- Tidak --> E[Set Strategi Awal = Max-Min]
    D --> F[Loop: Masih ada task belum dijadwalkan?]
    E --> F
    F -- Tidak --> G[Simulasi Eksekusi di CloudSim Selesai]
    F -- Ya --> H{Ronde Genap / Ganjil?}
    H -- Min-Min Turn --> I[Pilih task dengan Minimum Completion Time global]
    H -- Max-Min Turn --> J[Pilih task dengan Maximum Completion Time dari best-VM]
    I --> K[Assign Task ke VM & Update Waktu Tersedia VM]
    J --> K
    K --> L[Tandai task selesai dijadwalkan]
    L --> F
```

### 5.2 Algoritma Pembanding (Baseline)
1. **Round-Robin (RR):** Task dibagikan bergantian secara melingkar (VM 0, VM 1, ..., VM 14, VM 0, ...) tanpa mempertimbangkan panjang task maupun kapasitas MIPS VM.
2. **FCFS (Earliest Completion Time):** Task diproses sesuai urutan kedatangan asli, dan langsung diarahkan ke VM yang menawarkan waktu penyelesaian paling cepat pada saat itu.
3. **Min-Min Murni:** Selalu memilih task dengan completion time terkecil di setiap iterasi secara berulang hingga selesai.
4. **Max-Min Murni:** Selalu memilih task berukuran besar dengan completion time terbesar di setiap iterasi.

---

## 6. Fungsi Objektif dan Metrik Evaluasi

Simulasi dievaluasi menggunakan 6 metrik kinerja kuantitatif:

### 1. Makespan ($\mathcal{M}$)
Waktu total yang dibutuhkan data center untuk menyelesaikan seluruh cloudlet:
$$\mathcal{M} = \max_{i \in \{1 \dots N\}} (\text{FinishTime}_i)$$

### 2. Batas Bawah Teoretis Makespan ($\mathcal{M}_{\text{lower}}$) & Gap %
Batas waktu minimum absolut seandainya seluruh resource bekerja 100% tanpa idle satu detik pun:
$$\mathcal{M}_{\text{lower}} = \frac{\sum_{i=1}^N \text{Length}_i}{\sum_{j=1}^M \text{MIPS}_j} = \frac{82.341.000 \text{ MI}}{12.000 \text{ MIPS}} = 6.861,75 \text{ detik}$$
$$\text{Gap (\%)} = \frac{\mathcal{M} - \mathcal{M}_{\text{lower}}}{\mathcal{M}_{\text{lower}}} \times 100\%$$

### 3. Degree of Imbalance (DI)
Tingkat ketimpangan beban kerja antar-VM (semakin mendekati 0, semakin seimbang):
$$\text{DI} = \frac{T_{\max} - T_{\min}}{T_{\text{avg}}}$$
di mana $T_{\max}$, $T_{\min}$, dan $T_{\text{avg}}$ adalah waktu eksekusi total pada VM tersibuk, VM paling lengang, dan rata-rata seluruh VM.

### 4. Resource Utilization ($\mathcal{U}$)
Persentase efisiensi pemanfaatan kapasitas komputasi:
$$\mathcal{U} (\%) = \frac{T_{\text{avg}}}{\mathcal{M}} \times 100\%$$

### 5. Average Waiting Time ($W_{\text{avg}}$) & Turnaround Time ($TAT_{\text{avg}}$)
Indikator keadilan (*fairness*) dan respon sistem:
$$W_i = \text{ExecStartTime}_i - \text{SubmissionTime}_i, \quad W_{\text{avg}} = \frac{1}{N} \sum_{i=1}^N W_i$$
$$TAT_i = \text{FinishTime}_i - \text{SubmissionTime}_i, \quad TAT_{\text{avg}} = \frac{1}{N} \sum_{i=1}^N TAT_i$$

### 6. Throughput
Jumlah cloudlet yang berhasil diselesaikan per satuan detik simulasi:
$$\text{Throughput} = \frac{N}{\mathcal{M}} \quad (\text{task/detik})$$

---

## 7. Hasil Eksperimen & Analisis Pembahasan

Seluruh metrik berikut diekstraksi secara langsung dari eksekusi `DatasetMetricExtractor.java` menggunakan dataset `GoCJ_Dataset_600.txt` (tersimpan di direktori `hasil/`).

### 7.1 Perbandingan Performa 5 Algoritma (600 Cloudlet)

| Metrik Evaluasi | RASA | Round-Robin | FCFS | Min-Min | Max-Min |
|:---|:---:|:---:|:---:|:---:|:---:|
| **Makespan (detik)** | **6.918,10** | 13.432,10 | 7.484,60 | 7.349,60 | 6.873,60 |
| **Batas Bawah Ideal** | 6.861,75 | 6.861,75 | 6.861,75 | 6.861,75 | 6.861,75 |
| **Gap ke Batas Bawah** | **0,82%** | 95,75% | 9,08% | 7,11% | 0,17% |
| **Degree of Imbalance (DI)** | **0,0251** | 1,1684 | 0,1269 | 0,2200 | 0,0030 |
| **Resource Utilization (%)**| **99,11%** | 55,69% | 91,12% | 91,07% | 99,81% |
| **Avg Waiting Time (s)** | 4.251,86 | 3.576,09 | 3.154,86 | 1.806,21 | 4.886,61 |
| **Avg Turnaround Time (s)**| 4.423,28 | 3.763,09 | 3.325,37 | 1.973,54 | 5.058,13 |
| **Avg Execution Time (s)** | 171,42 | 187,00 | 170,51 | 167,34 | 171,52 |
| **Throughput (task/s)** | **0,0867** | 0,0447 | 0,0802 | 0,0816 | 0,0873 |
| **Std Dev Beban Antar-VM** | **42,47** | 2.642,34 | 217,70 | 614,53 | 6,30 |

### 7.2 Analisis Peningkatan Performa RASA terhadap Algoritma Lain

Tabel persentase keunggulan RASA dihitung dengan rumus:
$$\Delta (\%) = \frac{\text{Nilai}_{\text{Pembanding}} - \text{Nilai}_{\text{RASA}}}{\text{Nilai}_{\text{Pembanding}}} \times 100\%$$
*(Nilai positif menunjukkan RASA lebih unggul/lebih baik)*

| Algoritma Pembanding | Penghematan Makespan | Peningkatan DI (Load Balance) | Peningkatan Utilisasi | Speedup |
|:---|:---:|:---:|:---:|:---:|
| **vs Round-Robin** | **+48,50%** | **+97,85%** | **+77,98%** | **1,942×** |
| **vs FCFS** | **+7,57%** | **+80,23%** | **+8,77%** | **1,082×** |
| **vs Min-Min** | **+5,87%** | **+88,60%** | **+8,83%** | **1,062×** |
| **vs Max-Min** | -0,65% *(hampir identik)* | -739% *(sama-sama prima)* | -0,70% | 0,994× |

### 7.3 Analisis Keseimbangan Beban Kerja per Virtual Machine

Rincian alokasi beban pada RASA membuktikan efektivitas pemetaan beban kerja:

```
[ VM ID ]   [ Kapasitas ]   [ Jumlah Task ]   [ Beban Kerja (detik) ]   [ Utilisasi ]
 VM 0–8      1000 MIPS        38 – 44 task       6.833,5 s – 6.904,0 s     98,78% – 99,80%
 VM 9–14      500 MIPS        35 – 48 task       6.746,0 s – 6.918,0 s     97,51% – 100,0%
```

- **Sensitivitas Heterogenitas:** VM 500 MIPS menerima jumlah total instruksi (MI) yang proporsional dengan separuh dari VM 1000 MIPS, sehingga durasi sibuk seluruh VM selesai hampir bersamaan pada rentang 6.746 – 6.918 detik.
- Sebaliknya, pada **Round-Robin**, setiap VM dipaksa menerima 40 task tanpa mempedulikan MIPS. Akibatnya VM 500 MIPS mengalami kelebihan beban ekstrem (*overloaded*, berjalan hingga 13.432 detik) sementara VM 1000 MIPS menganggur sejak detik ke-6.000, menghasilkan pemborosan daya dan ketimpangan (*Degree of Imbalance* melonjak ke **1,168**).

### 7.4 Analisis Trade-off Makespan vs Waiting Time (Fairness)

Aspek penting yang menjadi fokus penilaian dosen adalah **trade-off antara efisiensi waktu selesai (Makespan) vs keadilan antrian (Waiting Time)**:

1. **Mengapa Makespan Min-Min lebih buruk daripada RASA?**
   Min-Min menuntaskan task-task kecil terlebih dahulu. Akibatnya, pada akhir proses antrian, tersisa sekumpulan task Extra Large yang harus dieksekusi secara serial di beberapa VM, memicu lonjakan makespan akhir (**7.349,60 detik**). Namun, karena 83% task kecil/menengah selesai di awal, *Average Waiting Time* Min-Min tampak sangat rendah (1.806 detik).
2. **Mengapa RASA lebih baik daripada Max-Min secara keseluruhan?**
   Max-Min meraih makespan 6.873 detik, namun mengorbankan task-task kecil. Task kecil terus tergeser ke antrian paling belakang, menyebabkan *Average Waiting Time* Max-Min melonjak ke **4.886,61 detik**.
   **RASA memberikan solusi ideal:** Melalui langkah berselang-seling (*interleaving*), RASA menyisipkan task kecil di setiap ronde Min-Min. Hasilnya:
   - *Average Waiting Time* RASA turun menjadi **4.251,86 detik** (**12,99% lebih cepat dan adil daripada Max-Min**).
   - *Average Turnaround Time* turun menjadi **4.423,28 detik** (**12,55% lebih cepat daripada Max-Min**).
   - Makespan RASA (6.918,10 detik) tetap terjaga luar biasa mendekati batas batas bawah (hanya berselisih tipis 44 detik atau 0,65% dari Max-Min).

### 7.5 Uji Robustness: Sweep 30 Urutan Task Acak (Mean ± Std Dev)

Untuk membuktikan kestabilan algoritma terhadap fluktuasi kedatangan task, dilakukan sweep 30 kali pengacakan urutan (*random seed* 1000–1029):

| Algoritma | Makespan (s) | Degree of Imbalance (DI) | Avg Turnaround Time (s) |
|:---|:---:|:---:|:---:|
| **RASA** | **6.918,10 ± 0,00** | **0,0251 ± 0,0000** | 4.423,28 ± 0,00 |
| **Max-Min** | 6.873,60 ± 0,00 | 0,0030 ± 0,0000 | 5.058,13 ± 0,00 |
| **Min-Min** | 7.349,60 ± 0,00 | 0,2200 ± 0,0000 | 1.973,54 ± 0,00 |
| **FCFS** | 7.335,85 ± 135,11 | 0,1055 ± 0,0256 | 3.379,38 ± 83,37 |
| **Round-Robin**| 14.176,43 ± 1.468,74 | 1,3158 ± 0,1914 | 3.918,86 ± 140,26 |

> **Interpretasi Statistik:**
> RASA memiliki **standar deviasi 0,00** untuk Makespan dan DI pada kondisi batch. Ini membuktikan RASA adalah algoritma heuristik global yang **sangat deterministik dan imun terhadap urutan kedatangan task**, tidak seperti FCFS dan Round-Robin yang performanya sangat rapuh dan berfluktuasi drastis tergantung urutan input.

### 7.6 Uji Skalabilitas Sistem (100, 200, 400, 600 Task)

Pengujian dilakukan untuk mengukur performa gap RASA terhadap batas bawah teoretis seiring bertambahnya beban task:

| Jumlah Task | Makespan RASA (s) | Gap ke Batas Bawah | Makespan RR (s) | Gap RR | Makespan FCFS (s) | Gap FCFS |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **100** | 1.144,95 | 5,26% | 2.946,80 | 170,34% | 1.541,80 | 41,50% |
| **200** | 2.286,70 | 2,38% | 5.556,80 | 147,47% | 2.726,30 | 22,05% |
| **400** | 4.624,75 | 1,21% | 9.669,00 | 111,60% | 5.070,05 | 10,96% |
| **600** | **6.918,10** | **0,82%** | 14.827,70 | 116,09% | 7.384,85 | 7,62% |

> **Analisis Skalabilitas:**
> Terlihat tren jelas bahwa semakin besar volume task, gap efisiensi RASA justru semakin menyusut (dari **5,26%** pada 100 task menjadi hanya **0,82%** pada 600 task). Hal ini mengindikasikan bahwa RASA memiliki skalabilitas superior dan semakin optimal saat diterapkan pada beban data center berskala masif.

---

## 8. Arsitektur Kode & Struktur Proyek

```
SIMULASI LENGKAP/
├── src/main/java/soka/simulasi/
│   ├── DatacenterCreator.java      # Pembuatan entitas Datacenter CloudSim & karakteristik SAN
│   ├── HostFactory.java            # Instansiasi 6 Host fisik (3 Host A + 3 Host B)
│   ├── VmFactory.java              # Instansiasi 15 VM (9 VM Tipe 1 + 6 VM Tipe 2)
│   ├── VmAllocationPolicyByType.java# Kebijakan kustom mapping VM ke Host berdasarkan tipe
│   ├── RasaScheduler.java          # Core logic heuristik RASA, Min-Min, dan Max-Min murni
│   ├── RasaBroker.java             # Ekstensi DatacenterBroker CloudSim penghubung RASA
│   ├── GoCjDatasetGenerator.java   # Parser & generator dataset GoCJ (4 kelas MI)
│   ├── DatasetMetricExtractor.java # Main runner komparasi 5 algoritma, sweep, & export hasil
│   ├── SimulasiTest.java           # Smoke test validasi runtime CloudSim
│   └── RasaSchedulerTest.java      # Unit test validasi algoritma RASA
├── dataset/
│   └── GoCJ_Dataset_600.txt        # Dataset asli 600 task Google Cloud Jobs
├── hasil/                          # Output artefak simulasi (otomatis dihasilkan)
│   ├── ringkasan_metrik.csv        # Tabel perbandingan utama 5 algoritma
│   ├── perbandingan.csv            # Persentase keunggulan relatif RASA
│   ├── sweep_acak.csv              # Hasil 30x pengacakan urutan (mean ± std dev)
│   ├── skala_task.csv              # Hasil evaluasi skala task 100, 200, 400, 600
│   ├── vm_{ALGO}.csv               # Beban kerja & utilisasi per-VM untuk 5 skenario
│   ├── cloudlet_{ALGO}.csv         # Detail waktu submit, start, finish per task
│   └── hasil_{ALGO}.json           # Raw summary metrik format JSON
├── lib/
│   ├── cloudsim-3.0.3.jar          # Core engine simulasi CloudSim
│   └── commons-math3-3.2.jar       # Pustaka komputasi statistik Apache
└── README.md                       # Laporan komprehensif proyek
```

---

## 9. Kesimpulan

Berdasarkan hasil eksperimen komparatif simulasi CloudSim 3.0.3 dengan beban kerja nyata GoCJ 600 task pada infrastruktur heterogen, dapat disimpulkan bahwa:

1. **Efisiensi Waktu Eksekusi Prima:** Algoritma RASA membuktikan efektivitasnya dengan menghasilkan makespan **6.918,10 detik**, memotong waktu eksekusi Round-Robin hingga **48,50%** dan FCFS sebesar **7,57%**, dengan selisih yang sangat tipis (**0,82%**) terhadap batas bawah optimal ideal.
2. **Kualitas Distribusi Beban Unggul:** Dengan nilai *Degree of Imbalance* sebesar **0,0251** dan utilisasi rata-rata **99,11%**, RASA berhasil mendistribusikan beban secara proporsional sesuai rasio komputasi VM 1000 MIPS dan VM 500 MIPS.
3. **Penyelesaian Dilema Starvation:** RASA terbukti berhasil menjadi jalan tengah terbaik antara Min-Min dan Max-Min. RASA mewarisi kecepatan makespan Max-Min sekaligus menurunkan waktu tunggu rata-rata (*Average Waiting Time*) hingga **12,99% lebih cepat dibandingkan Max-Min**, mencegah task kecil terabaikan dalam antrian.
4. **Stabilitas dan Skalabilitas Teruji:** Uji 30 pengacakan acak menunjukkan standar deviasi makespan 0,00 (sangat stabil), dan uji variasi skala task menunjukkan bahwa performa RASA semakin mendekati batas optimal seiring bertambahnya beban sistem.

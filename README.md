# SIMULASI — CloudSim 3.0.3

Simulasi datacenter CloudSim 3.0.3.

## Spesifikasi

- **Host Tipe A (3 unit, ID 0–2):** 4000 MIPS/PE × 8 PE, 16384 MB RAM, 10000 Mbps BW, 1000000 MB storage
- **Host Tipe B (3 unit, ID 3–5):** 2000 MIPS/PE × 4 PE, 8192 MB RAM, 5000 Mbps BW, 500000 MB storage
- **VM Tipe 1 (9 unit, ID 0–8):** 1000 MIPS, 1 PE, 2048 MB RAM, 1000 Mbps BW, 10000 MB image
- **VM Tipe 2 (6 unit, ID 9–14):** 500 MIPS, 1 PE, 1024 MB RAM, 500 Mbps BW, 5000 MB image
- Scheduler: `VmSchedulerTimeShared`, `CloudletSchedulerTimeShared`, `VmAllocationPolicySimple`

## Struktur Folder

```
SIMULASI/
├── src/main/java/soka/simulasi/   ← kode sumber (BOLEH diedit)
│   ├── HostFactory.java           ← createHosts() → List<Host> 6 unit
│   ├── VmFactory.java             ← createVms(brokerId) → List<Vm> 15 unit
│   ├── DatacenterCreator.java     ← createDatacenterShared(name) → Datacenter
│   └── SimulasiTest.java          ← smoke test (boleh dihapus/dipertahankan)
├── lib/                           ← JAR dependensi (JANGAN diutak-atik)
│   ├── cloudsim-3.0.3.jar
│   └── commons-math3-3.2.jar
├── bin/                           ← output .class (auto-generate, jangan edit manual)
├── target/                        ← output Maven (auto-generate, jika pakai mvn)
├── .vscode/settings.json          ← config VS Code (sourcePaths, referencedLibraries)
├── .classpath / .project          ← config Eclipse
├── pom.xml                        ← config Maven (opsional)
└── README.md                      ← file ini
```

| Path | Peran | Boleh diubah? |
|------|-------|---------------|
| `src/...` | Kode sumber peran | Ya |
| `lib/` | Dependensi JAR | Hanya tambah JAR baru |
| `bin/`, `target/` | Hasil compile | Tidak (otomatis) |
| `.vscode/`, `.classpath`, `.project`, `pom.xml` | Konfigurasi IDE/build | Ya, bila perlu |

## Prasyarat

- JDK 8 atau 11
- VS Code + **Extension Pack for Java**, atau Eclipse IDE
- Maven: opsional (project jalan tanpa Maven via `lib/`)

## Cara Pakai (untuk peran lain)

```java
import soka.simulasi.HostFactory;
import soka.simulasi.VmFactory;
import soka.simulasi.DatacenterCreator;
import java.util.List;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.Datacenter;

List<Host> hosts = HostFactory.createHosts();
List<Vm> vms = VmFactory.createVms(brokerId);
Datacenter dc = DatacenterCreator.createDatacenterShared("Datacenter_0");
```

> **Wajib:** setiap Cloudlet harus `cl.setUserId(brokerId)` sebelum
> `broker.submitCloudletList(...)`, kalau tidak muncul NPE di
> `Datacenter.processCloudletSubmit` (bug CloudSim 3.0.3).

## Komponen Integrasi dan Uji Coba

Komponen berikut ada di `src/main/java/soka/simulasi/`. Pembagian peran mengikuti tugas kelompok:

| File | Pemilik utama | Fungsi |
|------|---------------|--------|
| `RasaBroker.java` | Peran 3 | Adapter `DatacenterBroker`; `scheduleWithRasa(vms)` memanggil `RasaScheduler.schedule(...)` (Peran 2) dan mengikat cloudlet ke VM terpilih lewat `bindCloudletToVm(...)`. |
| `GoCjDatasetGenerator.java` | Peran 4 | Menyediakan panjang task 600 item dalam 4 kategori. Saat ini `generate600()` menghasilkan data sintetis seed 42; `loadFromFile(path)` tersedia untuk membaca data file. |
| `MetricsCalculator.java` | Peran 4 | Menghitung makespan, average execution time, average waiting time, resource utilization, dan degree of imbalance. |
| `RasaExperimentRunner.java` | Peran 5 | Entry point demo untuk membandingkan skenario RASA dan baseline FCFS menggunakan dataset yang sama. |

### Broker dan binding RASA

Tugas inti Peran 3 adalah menghubungkan scheduler murni dari Peran 2 dengan CloudSim. `RasaBroker` tidak menghitung ulang algoritma; ia mengambil panjang cloudlet dan MIPS VM, menyerahkannya ke `RasaScheduler`, lalu menerapkan hasil assignment pada cloudlet.

Urutan pemanggilan wajib:

1. Inisialisasi CloudSim dan buat datacenter.
2. Buat `RasaBroker`, lalu ambil `brokerId`.
3. Buat VM untuk broker dan panggil `broker.submitVmList(vms)`.
4. Siapkan cloudlet, isi `userId` dengan `brokerId`, lalu panggil `broker.submitCloudletList(cloudlets)`.
5. Panggil `broker.scheduleWithRasa(vms)` **setelah kedua list disubmit dan sebelum simulasi dimulai**. Urutan `vms` harus sama dengan urutan yang dikirim ke `submitVmList` karena index VM dari scheduler dipetakan ke VM dalam list tersebut.
6. Jalankan `CloudSim.startSimulation()` dan `CloudSim.stopSimulation()`.
7. Ambil hasil melalui `broker.getCloudletReceivedList()` untuk evaluasi.

Contoh inti integrasi:

```java
RasaBroker broker = new RasaBroker("Broker_RASA");
int brokerId = broker.getId();
List<Vm> vms = VmFactory.createVms(brokerId);
broker.submitVmList(vms);

List<Cloudlet> cloudlets = buildCloudlets(brokerId);
broker.submitCloudletList(cloudlets);
broker.scheduleWithRasa(vms);

CloudSim.startSimulation();
CloudSim.stopSimulation();
List<Cloudlet> hasil = broker.getCloudletReceivedList();
```

Tidak perlu mengisi VM cloudlet secara manual. Pastikan setiap cloudlet memiliki ID unik dan `cloudlet.setUserId(brokerId)`.

### Dataset dan metrik

- Peran 4 menyiapkan daftar panjang task GoCJ dalam MI dan membentuk `List<Cloudlet>`; ID cloudlet harus unik dan setiap cloudlet harus memiliki `userId` broker.
- Urutan cloudlet dalam list menjadi indeks task yang dipakai scheduler. Jangan menetapkan VM sendiri; binding dilakukan oleh `RasaBroker`.
- Proporsi tugas yang ditargetkan: Small 210, Medium 180, Large 150, Extra Large 60 (total 600).
- Setelah simulasi, gunakan `broker.getCloudletReceivedList()` bersama list VM untuk menghitung dan memvalidasi lima metrik di `MetricsCalculator`.
- `GoCjDatasetGenerator.generate600()` saat ini memakai data sintetis seed 42. Jika dataset minggu 3 mengharuskan data GoCJ asli, Peran 4 perlu memakai `loadFromFile(path)` dan runner perlu diarahkan ke hasil pemuatan itu.

Hasil kerja Peran 3 yang diserahkan adalah `RasaBroker.java` dan kontrak pemanggilan `scheduleWithRasa(vms)` di atas. Peran 4 dapat memakai generator/metrik yang tersedia sebagai titik awal, lalu memastikan sumber dataset dan definisi metrik sesuai ketentuan tugas.

Jalankan uji coba lengkap:

```powershell
javac -cp "lib/cloudsim-3.0.3.jar;lib/commons-math3-3.2.jar" -d bin src/main/java/soka/simulasi/*.java
java -cp "bin;lib/cloudsim-3.0.3.jar;lib/commons-math3-3.2.jar" soka.simulasi.RasaExperimentRunner
```

Output: log simulasi, ringkasan 5 metrik tiap skenario, tabel perbandingan RASA vs FCFS, dan 2 file CSV (`hasil_rasa.csv`, `hasil_fcfs.csv`) berisi beban kerja per VM.

> **Catatan implementasi:** VM di project ini memakai `CloudletSchedulerTimeShared`
> (beberapa cloudlet pada satu VM bisa berjalan konkuren). Karena itu metrik
> Resource Utilization & Degree of Imbalance dihitung dari **beban kerja**
> (panjang task ÷ MIPS VM), bukan dari penjumlahan `getActualCPUTime()` mentah,
> supaya tidak terjadi double-counting saat beberapa cloudlet tumpang tindih
> waktunya pada VM yang sama.

## Compile & Run

Terminal (dari folder `SIMULASI/`):

```powershell
javac -cp "lib/cloudsim-3.0.3.jar;lib/commons-math3-3.2.jar" -d bin src/main/java/soka/simulasi/*.java
java -cp "bin;lib/cloudsim-3.0.3.jar;lib/commons-math3-3.2.jar" soka.simulasi.SimulasiTest
```

- **VS Code:** `File > Open Folder...` → pilih `SIMULASI/` (otomatis pakai `.vscode/settings.json`), lalu Run `SimulasiTest`.
- **Eclipse:** `Import > Existing Projects into Workspace` → pilih `SIMULASI/`.
- **Maven (opsional):** `mvn compile`.

## Tambah File Baru

1. Taruh `.java` di `src/main/java/soka/simulasi/`.
2. Baris pertama: `package soka.simulasi;`
3. Compile ulang dengan perintah di atas.

## Troubleshooting

| Gejala | Solusi |
|--------|--------|
| `import org.cloudbus.cloudsim.*` merah | Pastikan buka folder `SIMULASI/` (bukan subfolder); cek JAR di `lib/` + JRE 8/11 (`Java: Configure Java Runtime`) |
| NPE `Datacenter.processCloudletSubmit` | Tambahkan `cl.setUserId(brokerId)` tiap Cloudlet |
| `mvn` not recognized | Wajar bila Maven belum install; pakai cara terminal/Eclipse/VS Code di atas |
| Hasil cloudlet 0/15 | Cek userId cloudlet + pastikan VM berhasil dibuat sebelum submit cloudlet |

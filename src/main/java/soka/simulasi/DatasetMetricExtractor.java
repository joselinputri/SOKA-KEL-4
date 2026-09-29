package soka.simulasi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

public class DatasetMetricExtractor {

    static final String[] ALGO = {"RASA", "RR", "FCFS", "MINMIN", "MAXMIN"};

    // Dataset: baca file GoCJ, ubah jadi cloudlet, 4 kategori sesuai draft
    static class Dataset {
        double[] mi;
        String[] kategori;
        String sumber;
        int ditambal;
        boolean apaAdanya;

        static String kategoriDari(double v) {
            for (GoCjDatasetGenerator.Category k : GoCjDatasetGenerator.KATEGORI) {
                if (v >= k.miMin && v <= k.miMax) {
                    return k.nama;
                }
            }
            return "Lainnya";
        }

        static String[] label(double[] mi) {
            String[] out = new String[mi.length];
            for (int i = 0; i < mi.length; i++) {
                out[i] = kategoriDari(mi[i]);
            }
            return out;
        }

        static Dataset load(String path, long seed) throws IOException {
            Dataset d = new Dataset();
            if (path == null || !new File(path).isFile()) {
                d.mi = GoCjDatasetGenerator.generate(seed);
                d.kategori = label(d.mi);
                d.sumber = "sintetis (seed " + seed + "), file dataset tidak ditemukan";
                return d;
            }

            double[] raw = GoCjDatasetGenerator.loadFromFile(path);
            if (raw.length == 600) {
                d.mi = raw;
                d.kategori = label(raw);
                d.sumber = path + " (600 job, dipakai apa adanya)";
                d.apaAdanya = true;
                return d;
            }

            Random rnd = new Random(seed);
            List<Double> nilai = new ArrayList<>();
            List<String> kat = new ArrayList<>();
            for (GoCjDatasetGenerator.Category k : GoCjDatasetGenerator.KATEGORI) {
                List<Double> pool = new ArrayList<>();
                for (double v : raw) {
                    if (kategoriDari(v).equals(k.nama)) {
                        pool.add(v);
                    }
                }
                Collections.shuffle(pool, rnd);
                for (int i = 0; i < k.jumlah; i++) {
                    if (i < pool.size()) {
                        nilai.add(pool.get(i));
                    } else {
                        nilai.add((double) (k.miMin + rnd.nextInt((int) (k.miMax - k.miMin + 1))));
                        d.ditambal++;
                    }
                    kat.add(k.nama);
                }
            }
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < nilai.size(); i++) {
                idx.add(i);
            }
            Collections.shuffle(idx, rnd);
            d.mi = new double[idx.size()];
            d.kategori = new String[idx.size()];
            for (int i = 0; i < idx.size(); i++) {
                d.mi[i] = nilai.get(idx.get(i));
                d.kategori[i] = kat.get(idx.get(i));
            }
            d.sumber = path;
            return d;
        }

        static Dataset acak(Dataset d, long seed) {
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < d.mi.length; i++) {
                idx.add(i);
            }
            Collections.shuffle(idx, new Random(seed));
            Dataset x = new Dataset();
            x.mi = new double[d.mi.length];
            x.kategori = new String[d.mi.length];
            for (int i = 0; i < idx.size(); i++) {
                x.mi[i] = d.mi[idx.get(i)];
                x.kategori[i] = d.kategori[idx.get(i)];
            }
            x.sumber = d.sumber;
            x.apaAdanya = d.apaAdanya;
            return x;
        }

        static Dataset ambil(Dataset d, int n) {
            Dataset x = new Dataset();
            x.mi = Arrays.copyOf(d.mi, n);
            x.kategori = Arrays.copyOf(d.kategori, n);
            x.sumber = d.sumber;
            return x;
        }

        List<Cloudlet> toCloudlets(int brokerId) {
            List<Cloudlet> list = new ArrayList<>();
            for (int i = 0; i < mi.length; i++) {
                Cloudlet c = new Cloudlet(i, (long) mi[i], 1, 300, 300,
                        new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
                c.setUserId(brokerId);
                list.add(c);
            }
            return list;
        }

        void print() {
            System.out.println("Sumber: " + sumber);
            for (GoCjDatasetGenerator.Category k : GoCjDatasetGenerator.KATEGORI) {
                int n = 0;
                for (String s : kategori) {
                    if (s.equals(k.nama)) {
                        n++;
                    }
                }
                System.out.printf("  %-12s %4d task (kuota draft %d)%n", k.nama, n, k.jumlah);
            }
            if (apaAdanya) {
                System.out.println("  Distribusi mengikuti data asli, bukan kuota draft.");
            }
            if (ditambal > 0) {
                System.out.println("  " + ditambal + " task ditambal data sintetis.");
            }
        }
    }

    // Baseline: penentuan VM dan urutan kirim untuk FCFS (round-robin pakai broker bawaan, tanpa binding)
    static class Baseline {
        int[] vm;
        int[] urutan;

        Baseline(double[] mi, List<Vm> vms) {
            int n = mi.length, m = vms.size();
            vm = new int[n];
            urutan = new int[n];
            double[] ready = new double[m];
            for (int i = 0; i < n; i++) {
                int v = terbaik(mi[i], ready, vms);
                ready[v] += mi[i] / vms.get(v).getMips();
                vm[i] = v;
                urutan[i] = i;
            }
        }

        static int terbaik(double mi, double[] ready, List<Vm> vms) {
            int best = 0;
            double bt = Double.MAX_VALUE;
            for (int v = 0; v < vms.size(); v++) {
                double t = ready[v] + mi / vms.get(v).getMips();
                if (t < bt) {
                    bt = t;
                    best = v;
                }
            }
            return best;
        }
    }

    // Metrics: hitung metrik dari cloudlet yang sudah selesai
    static class Metrics {
        String label;
        int jumlah;
        double makespan, avgExec, avgWait, avgTurn, util, di, throughput, stdBeban, batasBawah, gap;
        Map<Integer, Double> mips = new LinkedHashMap<>();
        Map<Integer, Double> beban = new LinkedHashMap<>();
        Map<Integer, Integer> taskPerVm = new LinkedHashMap<>();
        Map<String, double[]> perKategori = new TreeMap<>();
        List<Cloudlet> selesai;

        static Metrics hitung(String label, List<Cloudlet> selesai, List<Vm> vms) {
            Metrics m = new Metrics();
            m.label = label;
            m.selesai = selesai;
            m.jumlah = selesai.size();
            for (Vm v : vms) {
                m.mips.put(v.getId(), (double) v.getMips());
                m.beban.put(v.getId(), 0.0);
                m.taskPerVm.put(v.getId(), 0);
            }

            double exec = 0, wait = 0, turn = 0, totalMi = 0, totalMips = 0;
            for (Cloudlet c : selesai) {
                double tunggu = c.getExecStartTime() - c.getSubmissionTime();
                double ta = c.getFinishTime() - c.getSubmissionTime();
                m.makespan = Math.max(m.makespan, c.getFinishTime());
                exec += c.getActualCPUTime();
                wait += tunggu;
                turn += ta;
                totalMi += c.getCloudletLength();
                m.beban.merge(c.getVmId(), c.getCloudletLength() / m.mips.get(c.getVmId()), Double::sum);
                m.taskPerVm.merge(c.getVmId(), 1, Integer::sum);
                double[] a = m.perKategori.computeIfAbsent(Dataset.kategoriDari(c.getCloudletLength()), k -> new double[3]);
                a[0] += ta;
                a[1] += tunggu;
                a[2]++;
            }
            m.avgExec = exec / m.jumlah;
            m.avgWait = wait / m.jumlah;
            m.avgTurn = turn / m.jumlah;
            for (double v : m.mips.values()) {
                totalMips += v;
            }
            m.batasBawah = totalMi / totalMips;
            m.gap = (m.makespan / m.batasBawah - 1) * 100.0;

            double max = 0, min = Double.MAX_VALUE, total = 0;
            for (double b : m.beban.values()) {
                max = Math.max(max, b);
                min = Math.min(min, b);
                total += b;
            }
            double rata = total / vms.size();
            double var = 0;
            for (double b : m.beban.values()) {
                var += (b - rata) * (b - rata);
            }
            m.util = rata / m.makespan * 100.0;
            m.di = (max - min) / rata;
            m.stdBeban = Math.sqrt(var / vms.size());
            m.throughput = m.jumlah / m.makespan;
            return m;
        }

        double utilVm(int id) {
            return beban.get(id) / makespan * 100.0;
        }

        void print() {
            System.out.println("\n" + label);
            System.out.printf(Locale.US, "  Makespan              : %.4f%n", makespan);
            System.out.printf(Locale.US, "  Batas bawah makespan  : %.4f (selisih %.2f%%)%n", batasBawah, gap);
            System.out.printf(Locale.US, "  Avg execution time    : %.4f%n", avgExec);
            System.out.printf(Locale.US, "  Avg waiting time      : %.4f%n", avgWait);
            System.out.printf(Locale.US, "  Resource utilization  : %.2f%%%n", util);
            System.out.printf(Locale.US, "  Degree of imbalance   : %.4f%n", di);
            System.out.printf(Locale.US, "  Avg turnaround        : %.4f%n", avgTurn);
            System.out.printf(Locale.US, "  Throughput (task/s)   : %.4f%n", throughput);
            System.out.printf(Locale.US, "  Std beban antar VM    : %.4f%n", stdBeban);
            for (Map.Entry<String, double[]> e : perKategori.entrySet()) {
                double[] a = e.getValue();
                System.out.printf(Locale.US, "    %-12s turnaround %.2f, waiting %.2f%n", e.getKey(), a[0] / a[2], a[1] / a[2]);
            }
        }
    }

    // Perbandingan: RASA terhadap tiap pembanding, positif = RASA lebih baik
    static class Perbandingan {
        static double kecil(double base, double x) {
            return base == 0 ? 0 : (base - x) / base * 100;
        }

        static double besar(double base, double x) {
            return base == 0 ? 0 : (x - base) / base * 100;
        }

        static void tulis(Metrics rasa, List<Metrics> lain, String dir) throws IOException {
            System.out.println("\nRASA vs pembanding (positif = RASA lebih baik)");
            System.out.printf("%-8s %9s %9s %9s %9s %9s %9s%n",
                    "vs", "Makespan", "AvgTurn", "AvgWait", "DI", "Util", "Speedup");
            try (FileWriter w = new FileWriter(new File(dir, "perbandingan.csv"))) {
                w.write("pembanding,makespan_pct,avg_turnaround_pct,avg_waiting_pct,di_pct,utilisasi_pct,speedup\n");
                for (Metrics b : lain) {
                    double mk = kecil(b.makespan, rasa.makespan);
                    double tu = kecil(b.avgTurn, rasa.avgTurn);
                    double wt = kecil(b.avgWait, rasa.avgWait);
                    double di = kecil(b.di, rasa.di);
                    double ut = besar(b.util, rasa.util);
                    double sp = b.makespan / rasa.makespan;
                    System.out.printf(Locale.US, "%-8s %8.2f%% %8.2f%% %8.2f%% %8.2f%% %8.2f%% %7.3fx%n",
                            b.label, mk, tu, wt, di, ut, sp);
                    w.write(String.format(Locale.US, "%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                            b.label, mk, tu, wt, di, ut, sp));
                }
            }
        }
    }

    // Export: tulis hasil ke folder hasil/ (ringkasan, per VM, per cloudlet, JSON)
    static class Export {
        static void tulis(List<Metrics> hasil, String dir) throws IOException {
            new File(dir).mkdirs();
            try (FileWriter w = new FileWriter(new File(dir, "ringkasan_metrik.csv"))) {
                w.write("skenario,jumlah_cloudlet,makespan,batas_bawah,gap_pct,avg_execution_time,avg_waiting_time,"
                        + "avg_turnaround_time,resource_utilization_pct,degree_of_imbalance,throughput,std_beban_vm\n");
                for (Metrics m : hasil) {
                    w.write(String.format(Locale.US, "%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.6f,%.4f,%.6f%n",
                            m.label, m.jumlah, m.makespan, m.batasBawah, m.gap, m.avgExec, m.avgWait, m.avgTurn,
                            m.util, m.di, m.throughput, m.stdBeban));
                }
            }
            for (Metrics m : hasil) {
                vm(m, new File(dir, "vm_" + m.label + ".csv"));
                cloudlet(m, new File(dir, "cloudlet_" + m.label + ".csv"));
                json(m, new File(dir, "hasil_" + m.label + ".json"));
            }
            System.out.println("\nHasil ditulis ke " + new File(dir).getAbsolutePath());
        }

        static void vm(Metrics m, File f) throws IOException {
            try (FileWriter w = new FileWriter(f)) {
                w.write("vm_id,mips,jumlah_task,beban_kerja,utilisasi_pct\n");
                for (int id : m.beban.keySet()) {
                    w.write(String.format(Locale.US, "%d,%.0f,%d,%.4f,%.4f%n", id, m.mips.get(id),
                            m.taskPerVm.get(id), m.beban.get(id), m.utilVm(id)));
                }
            }
        }

        static void cloudlet(Metrics m, File f) throws IOException {
            try (FileWriter w = new FileWriter(f)) {
                w.write("cloudlet_id,vm_id,kategori,panjang_mi,submit,start,finish,exec_time,waiting_time,turnaround\n");
                for (Cloudlet c : m.selesai) {
                    w.write(String.format(Locale.US, "%d,%d,%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                            c.getCloudletId(), c.getVmId(), Dataset.kategoriDari(c.getCloudletLength()),
                            c.getCloudletLength(), c.getSubmissionTime(), c.getExecStartTime(), c.getFinishTime(),
                            c.getActualCPUTime(), c.getExecStartTime() - c.getSubmissionTime(),
                            c.getFinishTime() - c.getSubmissionTime()));
                }
            }
        }

        static void json(Metrics m, File f) throws IOException {
            StringBuilder s = new StringBuilder("{\n");
            s.append(String.format(Locale.US, "  \"skenario\": \"%s\",\n", m.label));
            s.append(String.format(Locale.US, "  \"makespan\": %.4f,\n", m.makespan));
            s.append(String.format(Locale.US, "  \"batas_bawah\": %.4f,\n", m.batasBawah));
            s.append(String.format(Locale.US, "  \"gap_pct\": %.4f,\n", m.gap));
            s.append(String.format(Locale.US, "  \"avg_execution_time\": %.4f,\n", m.avgExec));
            s.append(String.format(Locale.US, "  \"avg_waiting_time\": %.4f,\n", m.avgWait));
            s.append(String.format(Locale.US, "  \"avg_turnaround_time\": %.4f,\n", m.avgTurn));
            s.append(String.format(Locale.US, "  \"resource_utilization_pct\": %.4f,\n", m.util));
            s.append(String.format(Locale.US, "  \"degree_of_imbalance\": %.6f,\n", m.di));
            s.append(String.format(Locale.US, "  \"throughput\": %.6f,\n", m.throughput));
            s.append("  \"per_vm\": [\n");
            int i = 0;
            for (int id : m.beban.keySet()) {
                s.append(String.format(Locale.US,
                        "    {\"vm_id\": %d, \"mips\": %.0f, \"jumlah_task\": %d, \"beban_kerja\": %.4f, \"utilisasi_pct\": %.4f}%s\n",
                        id, m.mips.get(id), m.taskPerVm.get(id), m.beban.get(id), m.utilVm(id),
                        ++i < m.beban.size() ? "," : ""));
            }
            s.append("  ]\n}\n");
            try (FileWriter w = new FileWriter(f)) {
                w.write(s.toString());
            }
        }
    }

    // Simulasi: satu run CloudSim per algoritma
    static Metrics simulasi(String algo, Dataset d) throws Exception {
        CloudSim.init(1, Calendar.getInstance(), false);
        DatacenterCreator.createDatacenterShared("Datacenter_" + algo);

        boolean pakaiRasaBroker = algo.equals("RASA") || algo.equals("MINMIN") || algo.equals("MAXMIN");
        DatacenterBroker broker = pakaiRasaBroker ? new RasaBroker("Broker_" + algo) : new DatacenterBroker("Broker_" + algo);
        List<Vm> vms = VmFactory.createVms(broker.getId());
        broker.submitVmList(vms);
        List<Cloudlet> cloudlets = d.toCloudlets(broker.getId());
        broker.submitCloudletList(cloudlets);

        if (pakaiRasaBroker) {
            RasaScheduler.Mode mode = algo.equals("RASA") ? RasaScheduler.Mode.RASA
                    : algo.equals("MINMIN") ? RasaScheduler.Mode.MIN_MIN : RasaScheduler.Mode.MAX_MIN;
            ((RasaBroker) broker).scheduleWith(mode, vms);
        } else if (algo.equals("FCFS")) {
            Baseline b = new Baseline(d.mi, vms);
            List<Cloudlet> urut = new ArrayList<>();
            for (int idx : b.urutan) {
                Cloudlet c = cloudlets.get(idx);
                broker.bindCloudletToVm(c.getCloudletId(), vms.get(b.vm[idx]).getId());
                urut.add(c);
            }
            broker.getCloudletList().clear();
            broker.getCloudletList().addAll(urut);
        }
        // RR: tidak ada binding, pakai perilaku bawaan DatacenterBroker.

        CloudSim.startSimulation();
        CloudSim.stopSimulation();
        return Metrics.hitung(algo, broker.getCloudletReceivedList(), vms);
    }

    // Sweep: ulang dengan urutan task diacak, laporkan mean dan std
    static double mean(double[] v) {
        return Arrays.stream(v).average().orElse(0);
    }

    static double std(double[] v) {
        double m = mean(v), s = 0;
        for (double x : v) {
            s += (x - m) * (x - m);
        }
        return Math.sqrt(s / v.length);
    }

    static void sweep(Dataset d, int run, String dir) throws Exception {
        System.out.println("\nMean dan std dari " + run + " urutan task acak");
        System.out.printf("%-8s %-22s %-18s %-22s%n", "algo", "makespan", "DI", "avg turnaround");
        try (FileWriter w = new FileWriter(new File(dir, "sweep_acak.csv"))) {
            w.write("algo,makespan_mean,makespan_std,di_mean,di_std,avg_turnaround_mean,avg_turnaround_std,"
                    + "avg_waiting_mean,avg_waiting_std\n");
            for (String a : ALGO) {
                double[] mk = new double[run], di = new double[run], tu = new double[run], wt = new double[run];
                for (int r = 0; r < run; r++) {
                    Metrics m = simulasi(a, Dataset.acak(d, 1000 + r));
                    mk[r] = m.makespan;
                    di[r] = m.di;
                    tu[r] = m.avgTurn;
                    wt[r] = m.avgWait;
                }
                System.out.printf(Locale.US, "%-8s %9.2f +- %-9.2f %7.4f +- %-7.4f %9.2f +- %-8.2f%n",
                        a, mean(mk), std(mk), mean(di), std(di), mean(tu), std(tu));
                w.write(String.format(Locale.US, "%s,%.4f,%.4f,%.6f,%.6f,%.4f,%.4f,%.4f,%.4f%n", a,
                        mean(mk), std(mk), mean(di), std(di), mean(tu), std(tu), mean(wt), std(wt)));
            }
        }
    }

    // Skala: pengaruh jumlah task terhadap selisih ke batas bawah
    static void skala(Dataset d, int run, String dir) throws Exception {
        int[] ukuran = {100, 200, 400, 600};
        System.out.println("\nPengaruh jumlah task (mean dari " + run + " urutan acak, selisih ke batas bawah)");
        System.out.printf("%-6s", "task");
        for (String a : ALGO) {
            System.out.printf(" %-20s", a);
        }
        System.out.println();
        try (FileWriter w = new FileWriter(new File(dir, "skala_task.csv"))) {
            w.write("jumlah_task,algo,makespan_mean,gap_pct_mean\n");
            for (int n : ukuran) {
                System.out.printf("%-6d", n);
                for (String a : ALGO) {
                    double[] mk = new double[run], gp = new double[run];
                    for (int r = 0; r < run; r++) {
                        Metrics m = simulasi(a, Dataset.ambil(Dataset.acak(d, 2000 + r), n));
                        mk[r] = m.makespan;
                        gp[r] = m.gap;
                    }
                    System.out.printf(Locale.US, " %9.1f (%5.2f%%)   ", mean(mk), mean(gp));
                    w.write(String.format(Locale.US, "%d,%s,%.4f,%.4f%n", n, a, mean(mk), mean(gp)));
                }
                System.out.println();
            }
        }
    }

    // Main: args[0] = path dataset, args[1] = jumlah run sweep
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "dataset/GoCJ_Dataset_600.txt";
        int run = args.length > 1 ? Integer.parseInt(args[1]) : 30;

        Dataset d = Dataset.load(path, GoCjDatasetGenerator.SEED);
        d.print();

        Log.disable();
        List<Metrics> semua = new ArrayList<>();
        for (String a : ALGO) {
            semua.add(simulasi(a, d));
        }
        for (Metrics m : semua) {
            m.print();
        }

        Export.tulis(semua, "hasil");
        Perbandingan.tulis(semua.get(0), semua.subList(1, semua.size()), "hasil");
        sweep(d, run, "hasil");
        skala(d, Math.min(run, 10), "hasil");
        Log.enable();
    }
}
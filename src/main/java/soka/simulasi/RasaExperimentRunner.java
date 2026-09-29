package soka.simulasi;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

/**
 * Eksperimen utama: RASA vs Min-Min vs Max-Min vs Round Robin
 * pada dataset GoCJ ASLI (600 job).
 *
 * Jalankan dari root project:
 *   java -cp "bin;lib/cloudsim-3.0.3.jar;lib/commons-math3-3.2.jar" soka.simulasi.RasaExperimentRunner GoCJ_Dataset_600.txt
 */
public class RasaExperimentRunner {

    private static final int FILE_SIZE = 300, OUTPUT_SIZE = 300, PES_NUMBER = 1;

    /** Kategori sesuai isi file GoCJ asli. */
    static final String[] CAT = {"Small", "Medium", "Large", "XLarge", "Huge"};
    static final long[] CAT_MAX = {55_000, 99_999, 135_000, 337_500, Long.MAX_VALUE};

    static int category(double mi) {
        for (int i = 0; i < CAT_MAX.length; i++) {
            if (mi <= CAT_MAX[i]) return i;
        }
        return CAT.length - 1;
    }

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "GoCJ_Dataset_600.txt";
        double[] tasks = GoCjDatasetGenerator.loadFromFile(path);

        Log.disable(); // matikan log CloudSim yang verbose
        System.out.println("Dataset GoCJ asli dimuat dari " + path + ": " + tasks.length + " task");
        printDatasetProfile(tasks);

        String[] labels = {"RASA", "Min-Min", "Max-Min", "Round Robin"};
        List<MetricsCalculator.Result> all = new ArrayList<>();

        for (String label : labels) {
            List<Cloudlet> done = new ArrayList<>();
            MetricsCalculator.Result r = run(label, tasks, done);
            all.add(r);
            r.print();
            printWaitingPerCategory(done);
            exportCsv(label, done);
        }

        MetricsCalculator.printComparisonTable(all);

        double totalMi = 0;
        for (double t : tasks) totalMi += t;
        System.out.printf(Locale.US, "%nLower bound makespan (total MI / total MIPS) = %.4f detik%n",
                totalMi / (9 * 1000 + 6 * 500));
    }

    private static MetricsCalculator.Result run(String label, double[] tasks,
                                                List<Cloudlet> doneOut) throws Exception {
        CloudSim.init(1, Calendar.getInstance(), false);
        DatacenterCreator.createDatacenterShared("Datacenter_0");

        String safe = label.replace(" ", "").replace("-", "");
        DatacenterBroker broker;
        RasaBroker rb = null;
        if (label.equals("Round Robin")) {
            broker = new DatacenterBroker("Broker_" + safe); // round-robin bawaan CloudSim
        } else {
            rb = new RasaBroker("Broker_" + safe);
            broker = rb;
        }

        int brokerId = broker.getId();
        List<Vm> vms = VmFactory.createVms(brokerId);
        broker.submitVmList(vms);

        List<Cloudlet> cloudlets = new LinkedList<>();
        for (int i = 0; i < tasks.length; i++) {
            Cloudlet c = new Cloudlet(i, (long) tasks[i], PES_NUMBER, FILE_SIZE, OUTPUT_SIZE,
                    new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
            c.setUserId(brokerId); // wajib, kalau tidak NPE di Datacenter
            cloudlets.add(c);
        }
        broker.submitCloudletList(cloudlets);

        if (rb != null) { // WAJIB setelah submitCloudletList, sebelum startSimulation
            RasaScheduler.Mode mode = label.equals("RASA") ? RasaScheduler.Mode.RASA
                    : label.equals("Min-Min") ? RasaScheduler.Mode.MIN_MIN
                    : RasaScheduler.Mode.MAX_MIN;
            rb.scheduleWith(mode, vms);
        }

        CloudSim.startSimulation();
        CloudSim.stopSimulation();

        doneOut.addAll(broker.getCloudletReceivedList());
        return MetricsCalculator.compute(broker.getCloudletReceivedList(), vms, label);
    }

    private static void printDatasetProfile(double[] t) {
        int[] n = new int[CAT.length];
        for (double x : t) n[category(x)]++;
        System.out.println("Profil kategori (dari isi file):");
        for (int i = 0; i < CAT.length; i++) {
            System.out.printf(Locale.US, "  %-7s %4d task (%.1f%%)%n", CAT[i], n[i], 100.0 * n[i] / t.length);
        }
    }

    /** Fairness: rata-rata waktu tunggu per kategori ukuran task. */
    private static void printWaitingPerCategory(List<Cloudlet> done) {
        double[] sum = new double[CAT.length];
        int[] n = new int[CAT.length];
        for (Cloudlet c : done) {
            int k = category(c.getCloudletLength());
            sum[k] += c.getWaitingTime();
            n[k]++;
        }
        StringBuilder sb = new StringBuilder("  Avg waiting per kategori  :");
        for (int i = 0; i < CAT.length; i++) {
            sb.append(String.format(Locale.US, " %s=%.1f", CAT[i], n[i] == 0 ? 0 : sum[i] / n[i]));
        }
        System.out.println(sb);
    }

    /** Ekspor detail per-cloudlet ke CSV (bisa dibuka di Excel untuk grafik). */
    private static void exportCsv(String label, List<Cloudlet> done) {
        String f = "hasil_" + label.toLowerCase().replace(" ", "_").replace("-", "") + ".csv";
        done.sort(Comparator.comparingInt(Cloudlet::getCloudletId));
        try (FileWriter fw = new FileWriter(f)) {
            fw.write("cloudlet_id,length_mi,kategori,vm_id,waiting,exec,finish\n");
            for (Cloudlet c : done) {
                fw.write(String.format(Locale.US, "%d,%d,%s,%d,%.4f,%.4f,%.4f%n",
                        c.getCloudletId(), c.getCloudletLength(), CAT[category(c.getCloudletLength())],
                        c.getVmId(), c.getWaitingTime(), c.getActualCPUTime(), c.getFinishTime()));
            }
            System.out.println("  CSV diekspor: " + f);
        } catch (IOException e) {
            System.out.println("Gagal menulis " + f + ": " + e.getMessage());
        }
    }
}
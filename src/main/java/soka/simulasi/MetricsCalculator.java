package soka.simulasi;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

/**
 * 5 metrik evaluasi (draft bagian 4.1):
 * Makespan, Average Execution Time, Average Waiting Time,
 * Resource Utilization (%), Degree of Imbalance.
 */
public final class MetricsCalculator {

    private MetricsCalculator() {}

    public static final class Result {
        public final String label;
        public final int totalCloudlet;
        public final double makespan;
        public final double avgExecutionTime;
        public final double avgWaitingTime;
        public final double avgResourceUtilization; // %
        public final double degreeOfImbalance;
        public final Map<Integer, Double> busyTimePerVm;

        Result(String label, int totalCloudlet, double makespan, double avgExecutionTime,
               double avgWaitingTime, double avgResourceUtilization, double degreeOfImbalance,
               Map<Integer, Double> busyTimePerVm) {
            this.label = label;
            this.totalCloudlet = totalCloudlet;
            this.makespan = makespan;
            this.avgExecutionTime = avgExecutionTime;
            this.avgWaitingTime = avgWaitingTime;
            this.avgResourceUtilization = avgResourceUtilization;
            this.degreeOfImbalance = degreeOfImbalance;
            this.busyTimePerVm = busyTimePerVm;
        }

        public void print() {
            System.out.println();
            System.out.println("========== HASIL EVALUASI: " + label + " ==========");
            System.out.printf(Locale.US, "  Jumlah cloudlet selesai   : %d%n", totalCloudlet);
            System.out.printf(Locale.US, "  Makespan                  : %.4f detik%n", makespan);
            System.out.printf(Locale.US, "  Average Execution Time    : %.4f detik%n", avgExecutionTime);
            System.out.printf(Locale.US, "  Average Waiting Time      : %.4f detik%n", avgWaitingTime);
            System.out.printf(Locale.US, "  Resource Utilization (avg): %.2f %%%n", avgResourceUtilization);
            System.out.printf(Locale.US, "  Degree of Imbalance (DI)  : %.4f%n", degreeOfImbalance);
            System.out.println("=====================================================");
        }
    }

    /**
     * @param received cloudlet selesai (broker.getCloudletReceivedList())
     * @param vms      seluruh VM skenario ini
     * @param label    nama skenario
     */
    public static Result compute(List<Cloudlet> received, List<Vm> vms, String label) {
        int n = received.size();

        double makespan = 0, sumExec = 0, sumWait = 0;

        // Dengan CloudletSchedulerSpaceShared (1 PE) tidak ada cloudlet yang tumpang tindih
        // pada satu VM, jadi busy time = jumlah actualCPUTime cloudlet di VM tsb.
        Map<Integer, Double> busyPerVm = new HashMap<>();
        for (Vm vm : vms) busyPerVm.put(vm.getId(), 0.0);

        for (Cloudlet cl : received) {
            makespan = Math.max(makespan, cl.getFinishTime());
            sumExec += cl.getActualCPUTime();
            sumWait += cl.getWaitingTime();
            busyPerVm.merge(cl.getVmId(), cl.getActualCPUTime(), Double::sum);
        }

        double avgExec = n == 0 ? 0 : sumExec / n;
        double avgWait = n == 0 ? 0 : sumWait / n;

        double sumUtil = 0, tMax = 0, tMin = Double.MAX_VALUE, tSum = 0;
        for (double busy : busyPerVm.values()) {
            sumUtil += makespan > 0 ? (busy / makespan) * 100.0 : 0;
            tMax = Math.max(tMax, busy);
            tMin = Math.min(tMin, busy);
            tSum += busy;
        }
        double avgUtil = vms.isEmpty() ? 0 : sumUtil / vms.size();
        double tAvg = vms.isEmpty() ? 0 : tSum / vms.size();
        double di = tAvg > 0 ? (tMax - tMin) / tAvg : 0;

        return new Result(label, n, makespan, avgExec, avgWait, avgUtil, di, busyPerVm);
    }

    public static void printComparisonTable(List<Result> results) {
        System.out.println();
        System.out.println("================================= PERBANDINGAN SKENARIO =================================");
        System.out.printf(Locale.US, "%-14s %10s %14s %14s %10s %10s%n",
                "Skenario", "Makespan", "AvgExecTime", "AvgWaitTime", "Util(%)", "DI");
        for (Result r : results) {
            System.out.printf(Locale.US, "%-14s %10.4f %14.4f %14.4f %10.2f %10.4f%n",
                    r.label, r.makespan, r.avgExecutionTime, r.avgWaitingTime,
                    r.avgResourceUtilization, r.degreeOfImbalance);
        }
        System.out.println("=========================================================================================");
    }
}
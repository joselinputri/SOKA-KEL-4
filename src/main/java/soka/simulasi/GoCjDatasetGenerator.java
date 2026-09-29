package soka.simulasi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Peran 3 — Generator dataset uji coba bertipe GoCJ (Google Cloud Jobs).
 *
 * GoCJ asli (Hussain &amp; Aleem, 2018, Mendeley Data:
 * https://data.mendeley.com/datasets/b7bp6xhrcd) hanya berisi daftar
 * panjang job mentah (MI) tanpa label kategori. Sesuai draft desain
 * (bagian 1.2), 600 job dipilih lalu dikelompokkan ke 4 kategori
 * ukuran dengan proporsi berikut:
 *
 * <pre>
 *   Small        : 3.000   - 47.999  MI  -> 35% (210 task)
 *   Medium       : 48.000  - 99.999  MI  -> 30% (180 task)
 *   Large        : 100.000 - 449.999 MI  -> 25% (150 task)
 *   Extra Large  : 450.000 - 999.999 MI  -> 10% (60  task)
 * </pre>
 *
 * Karena file mentah GoCJ tidak diunggah ke repo, kelas ini membangkitkan
 * ulang 600 panjang task pada rentang yang SAMA seperti pada
 * {@code RasaSchedulerTest#testSkalaRealistikSepertiDatasetGoCJ()} (Peran 2),
 * memakai Random seed yang sama (42) supaya angka yang dipakai pada
 * simulasi CloudSim penuh (Peran 3) dan pada unit test (Peran 2) identik
 * dan hasilnya bisa saling diverifikasi silang.
 *
 * <p><b>Catatan:</b> Jika file dataset GoCJ asli (mis. {@code GoCJ_Dataset_600})
 * sudah tersedia di repo, ganti pemanggilan {@link #generate600()} dengan
 * {@link #loadFromFile(String)} agar memakai data riil, bukan sintetis.
 */
public final class GoCjDatasetGenerator {

    public static final int SEED = 42;

    private GoCjDatasetGenerator() {
    }

    /** Satu kategori ukuran task pada dataset GoCJ hasil klasifikasi. */
    public static final class Category {
        public final String nama;
        public final int jumlah;
        public final long miMin;
        public final long miMax;

        public Category(String nama, int jumlah, long miMin, long miMax) {
            this.nama = nama;
            this.jumlah = jumlah;
            this.miMin = miMin;
            this.miMax = miMax;
        }
    }

    /** Urutan & proporsi kategori persis sesuai draft desain bagian 1.2. */
    public static final Category[] KATEGORI = {
            new Category("Small", 210, 3_000, 47_999),
            new Category("Medium", 180, 48_000, 99_999),
            new Category("Large", 150, 100_000, 449_999),
            new Category("Extra Large", 60, 450_000, 999_999),
    };

    /**
     * Bangkitkan 600 panjang task (MI), diacak urutannya, dengan seed=42
     * (identik dengan data pada RasaSchedulerTest Peran 2).
     *
     * @return array 600 panjang task dalam MI
     */
    public static double[] generate600() {
        return generate(SEED);
    }

    /** Versi dengan seed custom, untuk keperluan uji ulang/sensitivitas. */
    public static double[] generate(long seed) {
        Random rnd = new Random(seed);
        List<Double> taskList = new ArrayList<>();
        for (Category c : KATEGORI) {
            addRange(taskList, rnd, c.jumlah, c.miMin, c.miMax);
        }
        Collections.shuffle(taskList, rnd);

        double[] out = new double[taskList.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = taskList.get(i);
        }
        return out;
    }

    private static void addRange(List<Double> list, Random rnd, int count, long lo, long hi) {
        for (int i = 0; i < count; i++) {
            list.add((double) (lo + rnd.nextInt((int) (hi - lo + 1))));
        }
    }

    /**
     * Muat panjang task (MI) dari file dataset GoCJ asli, satu angka per baris
     * (format asli file GoCJ_Dataset_&lt;N&gt; dari Mendeley Data).
     * Baris kosong/tidak valid akan dilewati.
     *
     * @param path path file dataset, mis. "dataset/GoCJ_Dataset_600"
     * @return array panjang task (MI) sesuai isi file
     */
    public static double[] loadFromFile(String path) throws java.io.IOException {
        List<Double> taskList = new ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    taskList.add(Double.parseDouble(line));
                } catch (NumberFormatException ignored) {
                    // lewati baris header/non-numerik jika ada
                }
            }
        }
        double[] out = new double[taskList.size()];
        for (int i = 0; i < out.length; i++) out[i] = taskList.get(i);
        return out;
    }
}

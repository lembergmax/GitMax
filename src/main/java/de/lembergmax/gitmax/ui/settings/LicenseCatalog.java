package de.lembergmax.gitmax.ui.settings;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.annotation.StringRes;

import de.lembergmax.gitmax.R;

import java.util.List;

/**
 * Die fremde Software, die in GitMax steckt, mit ihrer Lizenz. Die Lizenztexte liegen unter {@code res/raw}; die
 * Bedingungen der Lizenzen (Urheberhinweis und Lizenztext mitliefern) erfüllt der Über-Bildschirm damit auch in der
 * fertigen APK.
 */
final class LicenseCatalog {

    /** Ein Eintrag: Name, Kurzangabe (Urheber, Lizenz) und der Lizenztext. */
    record Entry(
            @StringRes int name,
            @StringRes int detail,
            @RawRes int text
    ) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry(R.string.license_jgit, R.string.license_jgit_detail, R.raw.license_edl_1_0),
            new Entry(R.string.license_sshd, R.string.license_sshd_detail, R.raw.license_apache_2_0),
            new Entry(R.string.license_javaewah, R.string.license_javaewah_detail, R.raw.license_apache_2_0),
            new Entry(R.string.license_eddsa, R.string.license_eddsa_detail, R.raw.license_cc0_1_0),
            new Entry(R.string.license_slf4j, R.string.license_slf4j_detail, R.raw.license_mit),
            new Entry(R.string.license_jcl, R.string.license_jcl_detail, R.raw.license_apache_2_0),
            new Entry(R.string.license_androidx, R.string.license_androidx_detail, R.raw.license_apache_2_0),
            new Entry(R.string.license_material, R.string.license_material_detail, R.raw.license_apache_2_0),
            new Entry(R.string.license_symbols, R.string.license_symbols_detail, R.raw.license_apache_2_0),
            new Entry(R.string.license_figtree, R.string.license_figtree_detail, R.raw.license_ofl_figtree),
            new Entry(R.string.license_commit_mono, R.string.license_commit_mono_detail, R.raw.license_ofl_commit_mono)
    );

    private LicenseCatalog() {
    }

    @NonNull
    static List<Entry> entries() {
        return ENTRIES;
    }
}

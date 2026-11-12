package com.example.engine;

import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import com.reandroid.app.AndroidManifest;
import com.reandroid.archive.ZipEntryMap;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.value.ValueType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Merges a split APK set (base.apk + config.*.apk, as found inside XAPK/APKS files)
 * into one APK, because the sandbox engine only understands single APKs.
 *
 * Follows the merge steps of REAndroid's APKEditor "merge" command (Apache-2.0),
 * using the ARSCLib library. The output is unsigned; {@link ApkResigner} signs it.
 */
public final class ApkMerger {

    private ApkMerger() {
    }

    /**
     * @param apkDir folder that contains only the .apk files of one app
     * @param outApk where to write the merged (unsigned) apk
     */
    public static void merge(File apkDir, File outApk) throws IOException {
        ApkBundle bundle = new ApkBundle();
        ApkModule merged = null;
        try {
            bundle.loadApkDirectory(apkDir, false);
            if (bundle.getApkModuleList().isEmpty()) {
                throw new IOException("No APK files found inside the package");
            }
            merged = bundle.mergeModules(false);

            // Old signatures are invalid after merging
            ZipEntryMap entries = merged.getZipEntryMap();
            entries.removeIf(Pattern.compile("^META-INF/.+\\.(([MS]F)|(RSA)|(DSA)|(EC))"));
            entries.remove("stamp-cert-sha256");
            merged.setApkSignatureBlock(null);

            sanitizeManifest(merged);
            merged.refreshTable();
            merged.refreshManifest();
            merged.writeApk(outApk);
        } finally {
            if (merged != null) {
                try { merged.close(); } catch (Exception ignored) { }
            }
            try { bundle.close(); } catch (Exception ignored) { }
        }
    }

    private static void sanitizeManifest(ApkModule module) {
        if (!module.hasAndroidManifest()) {
            return;
        }
        AndroidManifestBlock manifest = module.getAndroidManifest();
        ResXmlElement manifestElement = manifest.getManifestElement();
        if (manifestElement == null) {
            return;
        }
        // Tell Android this is no longer a split app
        manifestElement.removeAttributesWithId(AndroidManifest.ID_requiredSplitTypes);
        manifestElement.removeAttributesWithId(AndroidManifest.ID_splitTypes);
        manifestElement.removeAttributesWithName(AndroidManifest.NAME_requiredSplitTypes);
        manifestElement.removeAttributesWithName(AndroidManifest.NAME_splitTypes);
        if (AndroidManifest.ID_isSplitRequired != 0) {
            manifestElement.removeAttributesWithId(AndroidManifest.ID_isSplitRequired);
        }

        ResXmlElement application = manifestElement.getElement(AndroidManifest.TAG_application);
        if (application == null) {
            manifest.refresh();
            return;
        }
        if (AndroidManifest.ID_isSplitRequired != 0) {
            application.removeAttributesWithId(AndroidManifest.ID_isSplitRequired);
        }

        // Remove Play Store split / stamp meta-data (these make some apps refuse to start)
        List<ResXmlElement> toRemove = new ArrayList<>();
        Iterator<ResXmlElement> iterator = application.getElements(AndroidManifest.TAG_meta_data);
        while (iterator.hasNext()) {
            ResXmlElement meta = iterator.next();
            String name = metaName(meta);
            if (name == null) {
                continue;
            }
            if (name.startsWith("com.android.vending.")
                    || name.startsWith("com.android.stamp.")
                    || name.equals("com.android.dynamic.apk.fused.modules")) {
                toRemove.add(meta);
            }
        }
        for (ResXmlElement meta : toRemove) {
            application.remove(meta);
        }
        manifest.refresh();
    }

    private static String metaName(ResXmlElement meta) {
        ResXmlAttribute attribute = meta.searchAttributeByResourceId(AndroidManifest.ID_name);
        if (attribute == null || attribute.getValueType() != ValueType.STRING) {
            return null;
        }
        return attribute.getValueAsString();
    }
}

package com.example.engine

import android.content.Context
import com.android.apksig.ApkSigner
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Signs a merged XAPK. Android refuses unsigned APKs, and merging removes the
 * original signature, so VelvetBox signs it with its own key (assets/signer).
 */
object ApkResigner {

    fun sign(context: Context, input: File, output: File) {
        val keyBytes = context.assets.open("signer/repack.pk8").use { it.readBytes() }
        val privateKey: PrivateKey =
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val certificate = context.assets.open("signer/repack.x509.der").use { stream ->
            CertificateFactory.getInstance("X.509").generateCertificate(stream) as X509Certificate
        }

        val signerConfig = ApkSigner.SignerConfig.Builder(
            "VELVET",
            privateKey,
            listOf(certificate)
        ).build()

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(28)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .build()
            .sign()
    }
}

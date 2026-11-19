package dev.pallav.phonesaar

import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

class AdbManager(dir: File) : AbsAdbConnectionManager() {
    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(10, TimeUnit.SECONDS)
        val keyFile = File(dir, "adb_key.pk8")
        val certFile = File(dir, "adb_cert.der")
        if (keyFile.exists() && certFile.exists()) {
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            certificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certFile.readBytes()))
        } else {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            privateKey = pair.private
            certificate = selfSigned(pair.public.encoded, pair.private)
            keyFile.writeBytes(privateKey.encoded)
            certFile.writeBytes(certificate.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = privateKey

    override fun getCertificate(): Certificate = certificate

    override fun getDeviceName(): String = "PhoneSaar"

    private fun selfSigned(publicKey: ByteArray, signer: PrivateKey): Certificate {
        val now = System.currentTimeMillis()
        val name = X500Name("CN=PhoneSaar")
        val sigAlg = AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE)
        val tbs = V3TBSCertificateGenerator().apply {
            setSerialNumber(ASN1Integer(BigInteger.valueOf(now)))
            setIssuer(name)
            setSubject(name)
            setStartDate(Time(Date(now - 86_400_000L)))
            setEndDate(Time(Date(now + 3_650L * 86_400_000L)))
            setSignature(sigAlg)
            setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(publicKey))
        }.generateTBSCertificate()
        val sig = Signature.getInstance("SHA256withRSA").run {
            initSign(signer)
            update(tbs.getEncoded(ASN1Encoding.DER))
            sign()
        }
        val parts = ASN1EncodableVector().apply {
            add(tbs)
            add(sigAlg)
            add(DERBitString(sig))
        }
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(DERSequence(parts).encoded))
    }
}

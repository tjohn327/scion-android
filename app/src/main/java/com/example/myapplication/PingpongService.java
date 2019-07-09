package com.example.myapplication;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.spongycastle.asn1.ASN1ObjectIdentifier;
import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.asn1.x509.BasicConstraints;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.openssl.PEMKeyPair;
import org.spongycastle.openssl.PEMParser;
import org.spongycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.spongycastle.openssl.jcajce.JcaPEMWriter;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;

import pingpong.Pingpong;

public class PingpongService extends BackgroundService {

    public static final String PARAM_ARGS_QUERY = SciondService.class.getCanonicalName() + ".CONFIG_PATH";
    private static final int NID = 3;
    private static final String TAG = "pingpong";

    public PingpongService() { super("PingpongService"); }

    @Override
    protected int getNotificationId() {
        return NID;
    }

    @NonNull
    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        String arguments = intent.getStringExtra(PARAM_ARGS_QUERY);
        if (arguments == null) return;
        super.onHandleIntent(intent);

        log(R.string.servicesetup);

        try {
            File gencert = mkdir(Paths.get("gen-certs"));
            Provider bcProvider = new BouncyCastleProvider();
            Security.addProvider(bcProvider);
            File key = new File(gencert, "tls.key");
            File cert = new File(gencert, "tls.pem");
            KeyPair kp = null;
            JcaPEMWriter pWriter;
            if (!key.exists()) {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", bcProvider);
                kpg.initialize(2048);
                kp = kpg.generateKeyPair();
                pWriter = new JcaPEMWriter(new PrintWriter(key));
                pWriter.writeObject(kp);
                pWriter.close();
                log(R.string.pingpongcreatekey, key.getAbsolutePath());
            } else if (!cert.exists()) {
                PEMParser p = new PEMParser(new FileReader(key));
                JcaPEMKeyConverter conv = new JcaPEMKeyConverter();
                kp = conv.getKeyPair((PEMKeyPair) p.readObject());
                log(R.string.pingpongreadkey, key.getAbsolutePath());
            } else {
                log(R.string.pingpongcertexists, key.getAbsolutePath(), cert.getAbsolutePath());
            }
            if (!cert.exists() && kp != null) {
                ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(kp.getPrivate());
                JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                        new X500Name("CN=scion_def_srv"),
                        new BigInteger(160, new SecureRandom()),
                        new Date(),
                        new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10),
                        new X500Name("CN=scion_def_srv"),
                        kp.getPublic()
                );
                certBuilder.addExtension(new ASN1ObjectIdentifier("2.5.29.19"), true, new BasicConstraints(true));
                pWriter = new JcaPEMWriter(new PrintWriter(cert));
                pWriter.writeObject(new JcaX509CertificateConverter().setProvider(bcProvider).getCertificate(certBuilder.build(signer)));
                pWriter.close();
                log(R.string.pingpongcreatecert, cert.getAbsolutePath());
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            pw.close();
            Log.e(getTag(), sw.toString());
        }
        Pingpong.main(arguments, "", getFilesDir().getAbsolutePath());
    }
}
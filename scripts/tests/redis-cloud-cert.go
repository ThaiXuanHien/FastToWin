// Acceptance-only self-signed CA/server certificate. No public trust changes.
package main

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"math/big"
	"os"
	"path/filepath"
	"time"
)

func main() {
	if len(os.Args) != 2 { panic("fixture_certificate_path_required") }
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil { panic("fixture_certificate_key_failed") }
	template := &x509.Certificate{SerialNumber: big.NewInt(time.Now().UnixNano()), Subject: pkix.Name{CommonName: "receiver"}, NotBefore: time.Now().Add(-time.Minute), NotAfter: time.Now().Add(24*time.Hour), DNSNames: []string{"receiver", "localhost"}, IsCA: true, BasicConstraintsValid: true, KeyUsage: x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment | x509.KeyUsageCertSign, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil { panic("fixture_certificate_failed") }
	if os.WriteFile(filepath.Join(os.Args[1], "ca.pem"), pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}), 0600) != nil { panic("fixture_certificate_write_failed") }
	if os.WriteFile(filepath.Join(os.Args[1], "key.pem"), pem.EncodeToMemory(&pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(key)}), 0600) != nil { panic("fixture_key_write_failed") }
}

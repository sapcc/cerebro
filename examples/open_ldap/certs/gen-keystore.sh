#!/bin/sh

# Convert node certificate
cat root-ca.pem node1.pem node1-key.pem > combined-node1.pem
echo "Enter password for node1-cert.p12"
openssl pkcs12 -export -in combined-node1.pem -out node1-cert.p12 -name node1
echo "Enter password for keystore.jks"
keytool -importkeystore -srckeystore node1-cert.p12 -srcstoretype pkcs12 -destkeystore keystore.jks

# Convert admin certificate
cat root-ca.pem admin.pem admin-key.pem > combined-admin.pem
echo "Enter password for admin-cert.p12"
openssl pkcs12 -export -in combined-admin.pem -out admin-cert.p12 -name admin
echo "Enter password for keystore.jks"
keytool -importkeystore -srckeystore admin-cert.p12 -srcstoretype pkcs12 -destkeystore keystore.jks

# Import certificates to truststore
keytool -importcert -keystore truststore.jks -file root-ca.cer -storepass changeit -trustcacerts -deststoretype pkcs12

# Cleanup
rm combined-admin.pem
rm combined-node1.pem